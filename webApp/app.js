// API Endpoints
const API_BASE = "http://120.107.152.110/~a0303/DB";
const ENDPOINTS = {
    workers: `../get_workers.php`,
    meals: `./get_meals.php`,
    combos: `./get_combos.php`,
    equipment: `../get_equipment.php`,
    crud: `./crud.php`,
    combo_crud: `./combo_crud.php`,
    schedule: `./schedule.php`,
    recipes: `../get_recipes.php`,
    ingredients: `../get_ingredients.php`,
    mealcost: `../get_mealcost.php`,
    combo_explosion: `../get_combo_bom_explosion.php`,
    available_dates: `../get_available_dates.php`,
    consumption_report: `../get_consumption_report.php`
};

// Global App State
let appState = {
    strategy: "FCFS",
    workers: [],
    equipment: [],
    meals: [],
    combos: [],
    recipes: [],
    completedOrders: [],
    pendingQueue: [],
    orderBuffer: [],
    orderIdCounter: 1001,
    mockMode: false, // Automatically sets to true if remote server is unreachable
    isScriptRunning: false,
    scriptTimeoutIds: [] // Track running timeouts for cancellation
};

document.addEventListener("DOMContentLoaded", () => {
    initTabs();
    initOrderPanelTabs();
    initStrategySelector();
    initManualOrderPanel();
    initScriptRunner();
    initModal();
    initComboModal();
    initBOMSubTabs();
    initBOMFunctionality();
    
    // Initial Load
    refreshAllData();
    
    setInterval(() => {
        if (document.getElementById("tab-kitchen").classList.contains("active")) {
            loadKitchenQueue();
        }
    }, 4000);

    // Reload equipment/workers status every 6s (slightly offset from queue poll)
    setInterval(() => {
        if (document.getElementById("tab-kitchen").classList.contains("active")) {
            loadEquipmentAndWorkers();
        }
    }, 6000);

    // Update countdowns every second
    setInterval(() => {
        updateCountdowns();
    }, 1000);
});

function updateCountdowns() {
    const countdowns = document.querySelectorAll('.time-countdown');
    
    countdowns.forEach(span => {
        const expectedAt = parseFloat(span.getAttribute('data-expected'));
        let remaining = parseInt(span.getAttribute('data-remaining'));
        const prepTime = parseInt(span.getAttribute('data-preptime'));
        const autoCompleted = span.getAttribute('data-autocompleted');
        
        if (!expectedAt || expectedAt <= 0) {
            span.textContent = `${prepTime}秒`;
            return;
        }
        
        if (!isNaN(remaining) && remaining > 0) {
            remaining -= 1;
            span.setAttribute('data-remaining', remaining);
        } else if (remaining < 0) {
            remaining = 0;
        }
        
        span.textContent = `${remaining}秒`;

        if (remaining === 0) {
            span.style.color = '#ff4757';
            span.style.fontWeight = 'bold';
            // Auto-complete: trigger finishTask once when first hitting 0
            if (!autoCompleted) {
                span.setAttribute('data-autocompleted', '1');
                const orderId = span.getAttribute('data-orderid');
                const taskItem = span.getAttribute('data-taskitem');
                if (orderId && taskItem) {
                    finishTask(orderId, taskItem);
                }
            }
        } else {
            span.style.color = '';
            span.style.fontWeight = '';
        }
    });
}


// 1. App Tab Navigation
function initTabs() {
    const tabItems = document.querySelectorAll(".tab-bar .tab-item");
    const tabPanes = document.querySelectorAll(".app-content .tab-pane");

    tabItems.forEach(item => {
        item.addEventListener("click", () => {
            const targetTab = item.getAttribute("data-tab");
            
            tabItems.forEach(t => t.classList.remove("active"));
            tabPanes.forEach(p => p.classList.remove("active"));
            
            item.classList.add("active");
            document.getElementById(targetTab).classList.add("active");
            
            // Trigger load for specific tabs
            if (targetTab === "tab-kitchen") {
                loadKitchenQueue();
                loadEquipmentAndWorkers();
            } else if (targetTab === "tab-manager") {
                loadMealsTable();
                loadCombosTable();
            }
        });
    });
}

// 2. Order Panel Tabs (Manual vs Script)
function initOrderPanelTabs() {
    const tabs = document.querySelectorAll(".panel-tabs .panel-tab-btn");
    const contents = document.querySelectorAll(".panel-tab-content");

    tabs.forEach(tab => {
        tab.addEventListener("click", () => {
            const target = tab.getAttribute("data-panel-tab");
            
            tabs.forEach(t => t.classList.remove("active"));
            contents.forEach(c => c.classList.remove("active"));
            
            tab.classList.add("active");
            document.getElementById(target).classList.add("active");
        });
    });
}

// 3. Strategy (Algorithm) Selector
function initStrategySelector() {
    const selector = document.getElementById("strategy-selector");
    selector.addEventListener("change", async (e) => {
        appState.strategy = e.target.value;
        showConnectionStatus(`Switching mode to ${appState.strategy}...`, "info");
        
        if (appState.mockMode) {
            rescheduleLocal();
        } else {
            try {
                const response = await fetch(ENDPOINTS.schedule, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ type: "SWITCH_MODE", mode: appState.strategy })
                });
                const result = await response.json();
                
                if (result.error) {
                    showConnectionStatus(`Server Error: ${result.error}`, "offline");
                    console.error("Scheduler error response:", result);
                    return;
                }
                
                appState.pendingQueue = result;
                renderQueue();
                showConnectionStatus("Online - Mode Updated", "online");
            } catch (err) {
                console.warn("Could not communicate with Python scheduler. Falling back to local scheduler.");
                appState.mockMode = true;
                rescheduleLocal();
            }
        }
    });
}

// 4. Manual Order Panel Logic
function initManualOrderPanel() {
    const btnAddBuffer = document.getElementById("btn-add-buffer");
    const btnSendOrders = document.getElementById("btn-send-orders");
    const btnClearBuffer = document.getElementById("btn-clear-buffer");
    
    btnAddBuffer.addEventListener("click", () => {
        const itemSelect = document.getElementById("order-item-select");
        const countInput = document.getElementById("order-count-input");
        const takeoutCheckbox = document.getElementById("order-takeout-checkbox");
        
        const selectedValue = itemSelect.value;
        const count = parseInt(countInput.value) || 1;
        const isTakeout = takeoutCheckbox.checked;

        if (!selectedValue) {
            alert("請先選擇餐點或套餐！");
            return;
        }

        // Determine if selected value is a combo or a meal
        let itemName = "";
        let prepTime = 0;
        let isCombo = false;
        
        if (selectedValue.startsWith("combo:")) {
            const comboId = selectedValue.replace("combo:", "");
            const combo = appState.combos.find(c => String(c.combo_id || c.comboID) === comboId);
            itemName = combo.combo_name;
            prepTime = 0; // Combo prep time is calculated by adding steps in scheduler
            isCombo = true;
        } else {
            const mealId = selectedValue.replace("meal:", "");
            const meal = appState.meals.find(m => String(m.meal_id || m.mealID) === mealId);
            itemName = meal.meal_name;
            prepTime = meal.prep_time || 180;
        }

        addOrderToBuffer(itemName, prepTime, count, isTakeout, isCombo);
    });

    btnSendOrders.addEventListener("click", () => {
        submitBufferedOrders();
    });

    btnClearBuffer.addEventListener("click", () => {
        clearOrderBuffer();
    });
}

function addOrderToBuffer(item, prepTime, count, isTakeout, isCombo) {
    for (let i = 0; i < count; i++) {
        appState.orderBuffer.push({
            item: item,
            prep_time: prepTime,
            is_takeout: isTakeout,
            is_combo: isCombo
        });
    }
    renderOrderBuffer();
}

function renderOrderBuffer() {
    const list = document.getElementById("order-buffer-list");
    if (appState.orderBuffer.length === 0) {
        list.innerHTML = `<span class="empty-buffer">緩衝區為空</span>`;
        return;
    }

    list.innerHTML = appState.orderBuffer.map(order => {
        const tag = order.is_takeout ? "外帶" : "內用";
        return `<span class="buffer-item ${order.is_takeout ? 'takeout' : ''}">${tag}: ${order.item}</span>`;
    }).join("");
}

function clearOrderBuffer() {
    appState.orderBuffer = [];
    renderOrderBuffer();
}

async function submitBufferedOrders() {
    if (appState.orderBuffer.length === 0) {
        alert("緩衝區目前沒有訂單，請先點餐加入緩衝區！");
        return;
    }

    showConnectionStatus("Submitting orders...", "info");
    const ordersToSend = [];

    // Bundle orders with IDs
    appState.orderBuffer.forEach(order => {
        const orderId = appState.orderIdCounter++;
        
        if (order.is_combo) {
            const combo = appState.combos.find(c => c.combo_name === order.item);
            let foodItemIds = [];
            if (combo.food_items) {
                foodItemIds = combo.food_items.split(",").map(id => id.trim());
            }

            const subItems = [];
            foodItemIds.forEach(id => {
                const meal = appState.meals.find(m => String(m.meal_id || m.mealID) === id);
                if (meal) {
                    subItems.push({
                        item: meal.meal_name,
                        meal_name: meal.meal_name,
                        prep_time: meal.prep_time || 180
                    });
                }
            });

            ordersToSend.push({
                id: orderId,
                item: order.item,
                is_takeout: order.is_takeout,
                items: subItems
            });
        } else {
            ordersToSend.push({
                id: orderId,
                item: order.item,
                is_takeout: order.is_takeout,
                prep_time: order.prep_time
            });
        }
    });

    if (appState.mockMode) {
        // Run locally
        ordersToSend.forEach(order => {
            const isTakeout = order.is_takeout;
            const items = order.items || [{ item: order.item, prep_time: order.prep_time }];
            
            items.forEach(it => {
                appState.pendingQueue.push({
                    id: String(order.id),
                    item: it.item,
                    meal_name: it.item,
                    prep_time: it.prep_time,
                    description: it.item,
                    equipment_type: getEquipmentTypeByMeal(it.item),
                    arrival_time: Date.now(),
                    is_takeout: isTakeout,
                    task_index: 0
                });
            });
        });
        clearOrderBuffer();
        rescheduleLocal();
        showConnectionStatus("Offline - Running Local Mock Scheduler", "offline");
    } else {
        try {
            const res = await fetch(ENDPOINTS.schedule, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ type: "ADD_ORDER", data: ordersToSend })
            });
            const result = await res.json();
            
            if (result.error) {
                showConnectionStatus(`Server Error: ${result.error}`, "offline");
                console.error("Scheduler error response:", result);
                return;
            }

            appState.pendingQueue = result || [];
            clearOrderBuffer();
            renderQueue();
            loadEquipmentAndWorkers();
            showConnectionStatus("Online - Connected", "online");
        } catch (err) {
            console.error("Failed to submit orders to Python. Falling back to local scheduler.", err);
            appState.mockMode = true;
            // Execute locally with fallback
            ordersToSend.forEach(order => {
                const isTakeout = order.is_takeout;
                const items = order.items || [{ item: order.item, prep_time: order.prep_time }];
                items.forEach(it => {
                    appState.pendingQueue.push({
                        id: String(order.id),
                        item: it.item,
                        meal_name: it.item,
                        prep_time: it.prep_time,
                        description: it.item,
                        equipment_type: getEquipmentTypeByMeal(it.item),
                        arrival_time: Date.now(),
                        is_takeout: isTakeout,
                        task_index: 0
                    });
                });
            });
            clearOrderBuffer();
            rescheduleLocal();
            showConnectionStatus("Offline - Running Local Mock Scheduler", "offline");
        }
    }
}

// 5. Script Runner (JSON Script System)
function initScriptRunner() {
    const btnRun = document.getElementById("btn-run-script");
    const btnStop = document.getElementById("btn-stop-script");
    const btnPreset = document.getElementById("btn-load-preset-script");
    const txtInput = document.getElementById("script-json-input");
    const statusText = document.getElementById("script-status-text");

    btnPreset.addEventListener("click", () => {
        const preset = {
            "steps": [
                { "action": "SET_MODE", "takeout": true },
                { "action": "ADD_ORDER", "item": "薯條", "count": 1 },
                { "action": "WAIT", "seconds": 1 },
                { "action": "ADD_ORDER", "item": "大麥克", "count": 1 },
                { "action": "SEND_ORDERS" },
                { "action": "WAIT", "seconds": 2 },
                { "action": "SET_MODE", "takeout": false },
                { "action": "ADD_ORDER", "item": "可樂", "count": 2 },
                { "action": "SEND_ORDERS" }
            ]
        };
        txtInput.value = JSON.stringify(preset, null, 2);
    });

    btnRun.addEventListener("click", async () => {
        if (appState.isScriptRunning) return;
        
        let scriptData;
        try {
            scriptData = JSON.parse(txtInput.value);
            if (!scriptData || !Array.isArray(scriptData.steps)) {
                throw new Error("Missing 'steps' array in JSON");
            }
        } catch (err) {
            alert("JSON 腳本解析錯誤: " + err.message);
            return;
        }

        appState.isScriptRunning = true;
        btnRun.style.display = "none";
        btnStop.style.display = "block";
        statusText.textContent = "狀態: 腳本即將開始...";
        
        try {
            await executeScriptSteps(scriptData.steps);
            statusText.textContent = "狀態: 腳本執行完成";
        } catch (err) {
            statusText.textContent = "狀態: " + err.message;
        } finally {
            appState.isScriptRunning = false;
            btnRun.style.display = "block";
            btnStop.style.display = "none";
            appState.scriptTimeoutIds = [];
        }
    });

    btnStop.addEventListener("click", () => {
        appState.isScriptRunning = false;
        appState.scriptTimeoutIds.forEach(id => clearTimeout(id));
        statusText.textContent = "狀態: 腳本已被中斷";
    });
}

// Asynchronous runner mimicking Swing executeSteps
async function executeScriptSteps(steps) {
    const statusText = document.getElementById("script-status-text");
    const takeoutCheckbox = document.getElementById("order-takeout-checkbox");
    
    for (let i = 0; i < steps.length; i++) {
        if (!appState.isScriptRunning) {
            throw new Error("腳本已被使用者強制中斷");
        }

        const step = steps[i];
        const action = step.action || "";
        statusText.textContent = `狀態: 執行步驟 [${i + 1}/${steps.length}] - ${action}`;

        switch (action) {
            case "SET_MODE":
                const isTakeout = !!step.takeout;
                takeoutCheckbox.checked = isTakeout;
                break;
            case "ADD_ORDER":
                const itemName = step.item;
                const count = step.count || 1;
                
                // Lookup prep time
                let prepTime = 180;
                let isCombo = false;
                
                // Check meals first
                const meal = appState.meals.find(m => m.meal_name === itemName);
                if (meal) {
                    prepTime = meal.prep_time;
                } else {
                    const combo = appState.combos.find(c => c.combo_name === itemName);
                    if (combo) isCombo = true;
                }

                addOrderToBuffer(itemName, prepTime, count, takeoutCheckbox.checked, isCombo);
                break;
            case "SEND_ORDERS":
                await submitBufferedOrders();
                break;
            case "CLEAR_BUFFER":
                clearOrderBuffer();
                break;
            case "WAIT":
                const sec = step.seconds || 1;
                await new Promise((resolve, reject) => {
                    const tid = setTimeout(() => {
                        resolve();
                    }, sec * 1000);
                    appState.scriptTimeoutIds.push(tid);
                });
                break;
            case "REPEAT":
                const times = step.times || 1;
                const subSteps = step.steps;
                if (Array.isArray(subSteps)) {
                    for (let t = 0; t < times; t++) {
                        await executeScriptSteps(subSteps);
                    }
                }
                break;
            default:
                console.warn("未知腳本指令: " + action);
        }
        
        // Minor pacing pause
        await new Promise(r => setTimeout(r, 200));
    }
}

// 6. Data Loading and Dropdown Population
async function refreshAllData() {
    showConnectionStatus("Connecting to remote server...", "info");
    
    try {
        // Load configurations
        await Promise.all([
            loadWorkersList(),
            loadEquipmentList(),
            loadMealsList(),
            loadCombosList()
        ]);
        
        populateOrderDropdown();
        showConnectionStatus("Online - Connected", "online");
        loadKitchenQueue();
    } catch (err) {
        console.error("API error, entering offline mock mode:", err);
        showConnectionStatus("Offline - Running Local Mock Scheduler", "offline");
        appState.mockMode = true;
        
        // Mock data fallback
        appState.workers = [
            { workerID: 1, name: "陳小明" },
            { workerID: 2, name: "林大華" },
            { workerID: 3, name: "黃小美" }
        ];
        appState.equipment = [
            { equipmentID: "E1", name: "煎板 (Grill)", Etype: "grill", status: "" },
            { equipmentID: "E2", name: "炸爐 (Fryer)", Etype: "fryer", status: "" },
            { equipmentID: "E3", name: "備料檯 (Prep Station)", Etype: "prep_station", status: "" }
        ];
        appState.meals = [
            { meal_id: 1, meal_name: "大麥克", prep_time: 180 },
            { meal_id: 2, meal_name: "薯條", prep_time: 90 },
            { meal_id: 3, meal_name: "可樂", prep_time: 30 },
            { meal_id: 4, meal_name: "雞塊", prep_time: 120 },
            { meal_id: 5, meal_name: "玉米湯", prep_time: 60 }
        ];
        appState.combos = [
            { combo_id: 1, combo_name: "經典大麥克餐", food_items: "1,2,3" },
            { combo_id: 2, combo_name: "快樂雙人分享餐", food_items: "1,4,2,3" }
        ];
        
        populateOrderDropdown();
        generateMockOrders();
    }
}

async function loadWorkersList() {
    const res = await fetch(ENDPOINTS.workers);
    const data = await res.json();
    appState.workers = data.data || [];
}

async function loadEquipmentList() {
    const res = await fetch(ENDPOINTS.equipment);
    const data = await res.json();
    appState.equipment = data.data || [];
}

async function loadMealsList() {
    const res = await fetch(ENDPOINTS.meals);
    const data = await res.json();
    const rawMeals = data.data || [];
    appState.meals = rawMeals.map(m => {
        // Map DB prep_time or total_minutes to prep_time (seconds)
        let prep = parseInt(m.prep_time);
        if (isNaN(prep) || prep <= 0) {
            const mins = parseFloat(m.total_minutes) || 3;
            prep = Math.round(mins * 60);
        } else if (prep <= 30) {
            // Treat small values <= 30 as minutes, convert to seconds
            prep = prep * 60;
        }
        m.prep_time = prep;
        return m;
    });
}

async function loadCombosList() {
    const res = await fetch(ENDPOINTS.combos);
    const data = await res.json();
    const rawCombos = data.data || [];
    
    // Group raw rows (comboID, comboName, mealID) by comboID
    const combosMap = {};
    rawCombos.forEach(item => {
        const id = item.comboID || item.combo_id;
        const name = item.comboName || item.combo_name;
        const mealId = item.mealID || item.meal_id;
        
        if (!combosMap[id]) {
            combosMap[id] = {
                combo_id: id,
                combo_name: name,
                food_items: []
            };
        }
        if (mealId) {
            combosMap[id].food_items.push(mealId);
        }
    });
    
    // Convert to arrays of strings (comma separated) to match appState.combos structure
    appState.combos = Object.values(combosMap).map(c => {
        c.food_items = c.food_items.join(",");
        return c;
    });
}

function populateOrderDropdown() {
    const select = document.getElementById("order-item-select");
    
    let html = '<option value="">-- 請選擇 --</option>';

    if (appState.combos.length > 0) {
        html += '<optgroup label="🍱 特色組合套餐">';
        appState.combos.forEach(c => {
            html += `<option value="combo:${c.combo_id || c.comboID}">${c.combo_name}</option>`;
        });
        html += '</optgroup>';
    }

    if (appState.meals.length > 0) {
        html += '<optgroup label="🍔 單點美味餐點">';
        appState.meals.forEach(m => {
            html += `<option value="meal:${m.meal_id || m.mealID}">${m.meal_name} (${m.prep_time}秒)</option>`;
        });
        html += '</optgroup>';
    }

    select.innerHTML = html;
}

// 7. Kitchen View Rendering
async function loadKitchenQueue() {
    if (appState.mockMode) {
        renderQueue();
        return;
    }

    try {
        const res = await fetch(`${ENDPOINTS.schedule}?type=GET_STATUS`);
        const data = await res.json();
        
        if (data.error) {
            showConnectionStatus(`Server Error: ${data.error}`, "offline");
            console.error("Scheduler status fetch error:", data);
            return;
        }

        appState.pendingQueue = data || [];
        renderQueue();
    } catch (err) {
        appState.mockMode = true;
        showConnectionStatus("Offline - Running Local Mock", "offline");
        rescheduleLocal();
    }
}

async function loadEquipmentAndWorkers() {
    if (appState.mockMode) {
        renderEquipmentAndWorkers();
        return;
    }

    try {
        const res = await fetch(ENDPOINTS.equipment);
        const data = await res.json();
        appState.equipment = data.data || [];
        // pendingQueue is kept up-to-date by the 4s kitchen poll, just re-render
        renderEquipmentAndWorkers();
    } catch (err) {
        // Ignore
    }
}

function renderQueue() {
    const queueList = document.getElementById("kitchen-queue-list");
    const countBadge = document.getElementById("queue-count");
    
    if (!appState.pendingQueue || appState.pendingQueue.length === 0) {
        queueList.innerHTML = `<div class="empty-state">目前無排程中的訂單</div>`;
        countBadge.textContent = "0";
        return;
    }

    countBadge.textContent = appState.pendingQueue.length;

    // Group items by Order ID
    const ordersMap = {};
    appState.pendingQueue.forEach(task => {
        const orderId = task.id;
        if (!ordersMap[orderId]) {
            ordersMap[orderId] = {
                id: orderId,
                is_takeout: task.is_takeout,
                tasks: []
            };
        }
        ordersMap[orderId].tasks.push(task);
    });

    let html = "";
    Object.values(ordersMap).forEach(order => {
        const isTakeoutClass = order.is_takeout ? "takeout" : "dinein";
        const isTakeoutLabel = order.is_takeout ? "外帶" : "內用";
        
        html += `
        <div class="order-group ${order.is_takeout ? 'takeout' : ''}">
            <div class="order-group-header">
                <span class="order-id">訂單單號: #${order.id}</span>
                <span class="order-type ${isTakeoutClass}">${isTakeoutLabel}</span>
            </div>
            <div class="order-tasks-list">
        `;

        order.tasks.forEach(task => {
            html += `
                <div class="task-item ${task.worker_id ? 'active' : ''}">
                    <div class="task-info">
                        <span class="task-name">${task.description || task.item}</span>
                        <div class="task-meta">
                            <span>員工: W${task.worker_id || "未分配"}</span>
                            <span>設備: ${task.equipment_name || "無需設備"}</span>
                        </div>
                    </div>
                    <div class="task-actions">
                        <span class="time-countdown" 
                            data-expected="${task.expected_at || 0}" 
                            data-remaining="${task.remaining_time !== undefined ? task.remaining_time : task.prep_time}" 
                            data-preptime="${task.prep_time}"
                            data-orderid="${order.id}"
                            data-taskitem="${(task.item || '').replace(/'/g, '&apos;')}"
                        >${task.remaining_time !== undefined ? task.remaining_time : task.prep_time}秒</span>
                        <button onclick="finishTask('${order.id}', '${(task.item || '').replace(/'/g, "\\'")}')" class="btn btn-primary" style="padding: 4px 10px; font-size: 11px;">完成</button>
                    </div>
                </div>
            `;
        });

        html += `
            </div>
        </div>
        `;
    });

    queueList.innerHTML = html;
}

function renderEquipmentAndWorkers() {
    const equipList = document.getElementById("equipment-status-list");
    const workerList = document.getElementById("worker-status-list");

    // Only tasks that still have time remaining are "actively using" equipment/workers
    const activeTasks = appState.pendingQueue.filter(t => (t.remaining_time || 0) > 0);

    // Render Equipment
    let equipHtml = "";
    appState.equipment.forEach(eq => {
        const eqId = String(eq.equipmentID || eq.equipment_id || "");
        const currentTask = activeTasks.find(t =>
            String(t.equipment_id) === eqId ||
            (t.equipment_name && eq.name && t.equipment_name === eq.name)
        );
        const isBusy = !!currentTask;
        const statusText = currentTask
            ? `使用中 (W${currentTask.worker_id}: ${currentTask.item}) - ${currentTask.remaining_time}秒`
            : '空閒 (Idle)';
        
        equipHtml += `
            <div class="status-tile">
                <div class="status-tile-title">${eq.name || eqId}</div>
                <div class="status-tile-value ${isBusy ? 'busy' : 'idle'}">
                    ${statusText}
                </div>
            </div>
        `;
    });
    equipList.innerHTML = equipHtml || '<div class="empty-state">無設備資訊</div>';

    // Render Workers
    let workerHtml = "";
    appState.workers.forEach(w => {
        const wId = String(w.workerID || w.worker_id || "");
        const currentTask = activeTasks.find(t => String(t.worker_id) === wId);
        const isBusy = !!currentTask;
        const statusText = currentTask
            ? `製作中 (${currentTask.item}) - ${currentTask.remaining_time}秒`
            : '待命中 (Idle)';
        
        workerHtml += `
            <div class="status-tile">
                <div class="status-tile-title">W${wId} - ${w.name || '員工'}</div>
                <div class="status-tile-value ${isBusy ? 'busy' : 'idle'}">
                    ${statusText}
                </div>
            </div>
        `;
    });
    workerList.innerHTML = workerHtml || '<div class="empty-state">無員工資訊</div>';
}


// 8. Finish Task Action
async function finishTask(orderId, taskItem) {
    if (appState.mockMode) {
        appState.pendingQueue = appState.pendingQueue.filter(t => !(t.id === orderId && t.item === taskItem));
        const orderStillHasTasks = appState.pendingQueue.some(t => t.id === orderId);
        if (!orderStillHasTasks) {
            const now = new Date();
            const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
            appState.completedOrders.unshift({
                id: orderId,
                content: taskItem || "單點餐點",
                time: timeStr
            });
            renderCompletedOrders();
        }
        rescheduleLocal();
        return;
    }

    try {
        const res = await fetch(ENDPOINTS.schedule, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ type: "FINISH_ORDER", order_id: orderId, item: taskItem })
        });
        const result = await res.json();
        
        if (result && result.error) {
            showConnectionStatus(`Server Error: ${result.error}`, "offline");
            console.error("Scheduler finish order error response:", result);
            return;
        }

        // Python remove_finished returns {queue, all_items_completed, order_content}
        if (result && result.queue !== undefined) {
            appState.pendingQueue = Array.isArray(result.queue) ? result.queue : [];
        } else if (Array.isArray(result)) {
            // Fallback: might return plain array
            appState.pendingQueue = result;
        }
        
        renderQueue();
        renderEquipmentAndWorkers();
        
        if (result && result.all_items_completed) {
            const orderContent = result.order_content || taskItem;
            const now = new Date();
            const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
            
            // Add to completed orders
            appState.completedOrders.unshift({
                id: orderId,
                content: orderContent,
                time: timeStr
            });
            
            renderCompletedOrders();
            alert(`訂單 #${orderId} (${orderContent}) 已全部製作完畢！`);
        }
    } catch (err) {
        console.error("Failed to finish task:", err);
    }
}

// Helper: Equip matcher for local mock
function getEquipmentTypeByMeal(mealName) {
    if (mealName.includes("薯條") || mealName.includes("雞塊")) return "fryer";
    if (mealName.includes("大麥克") || mealName.includes("牛肉餅") || mealName.includes("熱狗") || mealName.includes("麵包")) return "grill";
    if (mealName.includes("備料") || mealName.includes("生菜")) return "prep_station";
    return "";
}

// 9. Meal Manager Table Loading
async function loadMealsTable() {
    if (appState.meals.length === 0) {
        try {
            await loadMealsList();
        } catch (err) {
            // Ignore
        }
    }
    
    const tableBody = document.querySelector("#meals-table tbody");
    let html = "";
    appState.meals.forEach(meal => {
        html += `
            <tr>
                <td>${meal.meal_id || meal.mealID}</td>
                <td><strong>${meal.meal_name}</strong></td>
                <td>${meal.prep_time} 秒</td>
                <td>
                    <button onclick="deleteMeal(${meal.meal_id || meal.mealID})" class="btn btn-danger">刪除</button>
                </td>
            </tr>
        `;
    });
    tableBody.innerHTML = html || '<tr><td colspan="4" class="empty-state">無餐點資料</td></tr>';
}

async function deleteMeal(mealId) {
    if (!confirm("確定要刪除此餐點嗎？")) return;

    try {
        const response = await fetch(`${ENDPOINTS.crud}?action=delete&id=${mealId}`);
        const resData = await response.json();
        
        if (resData.success) {
            alert("刪除成功！");
            await loadMealsList();
            loadMealsTable();
        } else {
            alert("刪除失敗: " + (resData.message || "未知錯誤"));
        }
    } catch (err) {
        alert("刪除失敗，API 連線異常。");
    }
}

// Helper: Connection status display
function showConnectionStatus(message, type) {
    const indicator = document.getElementById("connection-status");
    indicator.textContent = message;
    indicator.className = "status-indicator " + type;
}

// Modal init
function initModal() {
    const modal = document.getElementById("add-meal-modal");
    const openBtn = document.getElementById("btn-add-meal");
    const closeBtn = document.querySelector(".close-modal");
    const form = document.getElementById("add-meal-form");

    openBtn.addEventListener("click", () => modal.classList.add("active"));
    closeBtn.addEventListener("click", () => modal.classList.remove("active"));
    window.addEventListener("click", (e) => {
        if (e.target === modal) modal.classList.remove("active");
    });

    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        const mealName = document.getElementById("meal-name").value;
        const prepTime = document.getElementById("meal-prep").value;

        try {
            const response = await fetch(`${ENDPOINTS.crud}?action=insert`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ meal_name: mealName, prep_time: parseInt(prepTime) })
            });
            const resData = await response.json();
            
            if (resData.success || resData.meal_id) {
                alert("餐點新增成功！");
                form.reset();
                modal.classList.remove("active");
                loadMealsTable();
            } else {
                alert("新增失敗: " + (resData.message || "未知錯誤"));
            }
        } catch (err) {
            alert("API 連線失敗，新增作業無法完成。");
        }
    });
}

// 9b. Combo Manager Table Loading
async function loadCombosTable() {
    if (appState.combos.length === 0) {
        try {
            await loadCombosList();
        } catch (err) {
            // Ignore
        }
    }
    if (appState.meals.length === 0) {
        try {
            await loadMealsList();
        } catch (err) {
            // Ignore
        }
    }
    
    const tableBody = document.querySelector("#combos-table tbody");
    let html = "";
    
    appState.combos.forEach(combo => {
        // Resolve meal names from food_items IDs string
        let itemNames = [];
        if (combo.food_items) {
            const ids = combo.food_items.split(",");
            ids.forEach(id => {
                const meal = appState.meals.find(m => String(m.meal_id || m.mealID) === id.trim());
                if (meal) {
                    itemNames.push(meal.meal_name);
                }
            });
        }
        
        html += `
            <tr>
                <td>${combo.combo_id || combo.comboID}</td>
                <td><strong>${combo.combo_name}</strong></td>
                <td>${itemNames.join(", ") || "無品項"}</td>
                <td>
                    <button onclick="deleteCombo(${combo.combo_id || combo.comboID})" class="btn btn-danger">刪除</button>
                </td>
            </tr>
        `;
    });
    tableBody.innerHTML = html || '<tr><td colspan="4" class="empty-state">無套餐資料</td></tr>';
}

async function deleteCombo(comboId) {
    if (!confirm("確定要刪除此套餐嗎？")) return;

    try {
        const response = await fetch(`${ENDPOINTS.combo_crud}?action=delete&id=${comboId}`);
        const resData = await response.json();
        
        if (resData.status === "success" || resData.success) {
            alert("刪除成功！");
            await loadCombosList();
            loadCombosTable();
            populateOrderDropdown(); // Refresh dropdown
        } else {
            alert("刪除失敗: " + (resData.message || "未知錯誤"));
        }
    } catch (err) {
        alert("刪除失敗，API 連線異常。");
    }
}

// Combo Modal Init
function initComboModal() {
    const modal = document.getElementById("add-combo-modal");
    const openBtn = document.getElementById("btn-add-combo");
    const closeBtn = document.querySelector(".close-combo-modal");
    const form = document.getElementById("add-combo-form");
    const checkboxContainer = document.getElementById("combo-meals-checkboxes");

    openBtn.addEventListener("click", async () => {
        // Ensure meals list is loaded
        if (appState.meals.length === 0) {
            try {
                await loadMealsList();
            } catch (err) {}
        }
        
        // Render checkboxes
        let html = "";
        appState.meals.forEach(m => {
            html += `
                <label class="checkbox-container" style="margin-bottom: 4px; display: flex; align-items: center;">
                    <input type="checkbox" name="combo-meals" value="${m.meal_id || m.mealID}">
                    <span class="checkmark"></span>
                    ${m.meal_name}
                </label>
            `;
        });
        checkboxContainer.innerHTML = html || '<div class="empty-state">請先新增單點品項</div>';
        
        modal.classList.add("active");
    });

    closeBtn.addEventListener("click", () => modal.classList.remove("active"));
    window.addEventListener("click", (e) => {
        if (e.target === modal) modal.classList.remove("active");
    });

    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        const comboName = document.getElementById("combo-name-input").value;
        
        // Collect checked meal IDs
        const checkedBoxes = document.querySelectorAll('input[name="combo-meals"]:checked');
        const selectedMealIds = Array.from(checkedBoxes).map(cb => cb.value);
        
        if (selectedMealIds.length === 0) {
            alert("請至少選擇一個單點品項！");
            return;
        }

        try {
            const response = await fetch(`${ENDPOINTS.combo_crud}?action=insert`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    combo_name: comboName,
                    food_items: selectedMealIds
                })
            });
            const resData = await response.json();
            
            if (resData.status === "success" || resData.success || resData.combo_id) {
                alert("套餐新增成功！");
                form.reset();
                modal.classList.remove("active");
                await loadCombosList();
                loadCombosTable();
                populateOrderDropdown(); // Refresh dropdown
            } else {
                alert("新增失敗: " + (resData.message || "未知錯誤"));
            }
        } catch (err) {
            alert("API 連線失敗，新增作業無法完成。");
        }
    });
}

// ==========================================
// 10. Offline Mock Scheduler (JavaScript Fallback)
// ==========================================

function generateMockOrders() {
    appState.pendingQueue = [
        { id: "1001", item: "牛肉餅", meal_name: "大麥克_0", prep_time: 45, equipment_type: "grill", is_takeout: false, arrival_time: Date.now() },
        { id: "1001", item: "烤麵包", meal_name: "大麥克_0", prep_time: 20, equipment_type: "grill", is_takeout: false, arrival_time: Date.now() },
        { id: "1002", item: "炸薯條", meal_name: "薯條_0", prep_time: 90, equipment_type: "fryer", is_takeout: true, arrival_time: Date.now() + 1000 },
        { id: "1003", item: "備可樂", meal_name: "可樂_0", prep_time: 15, equipment_type: "", is_takeout: false, arrival_time: Date.now() + 2000 }
    ];
    rescheduleLocal();
}

function rescheduleLocal() {
    const strategy = appState.strategy;
    let queue = [...appState.pendingQueue];

    // 1. Sort according to Strategy
    if (strategy === "SJF") {
        queue.sort((a, b) => a.prep_time - b.prep_time);
    } else if (strategy === "AGING") {
        const now = Date.now();
        queue.forEach(task => {
            const waitTime = (now - task.arrival_time) / 1000; // in seconds
            task.priority = waitTime / (task.prep_time || 1);
        });
        queue.sort((a, b) => b.priority - a.priority);
    } else {
        queue.sort((a, b) => a.arrival_time - b.arrival_time);
    }

    // 2. Allocate workers and equipment
    let workerTimes = {}; 
    let equipmentTimes = {}; 

    appState.workers.forEach(w => workerTimes[w.workerID] = 0);
    appState.equipment.forEach(e => {
        equipmentTimes[e.equipmentID] = 0;
        e.status = ""; 
    });

    const scheduledQueue = queue.map(task => {
        const selectedWorker = Object.keys(workerTimes).reduce((a, b) => workerTimes[a] <= workerTimes[b] ? a : b);
        const workerStart = workerTimes[selectedWorker];

        let selectedEquip = null;
        let equipStart = workerStart;

        if (task.equipment_type) {
            const match = appState.equipment.find(e => e.Etype === task.equipment_type);
            if (match) {
                selectedEquip = match;
                equipStart = equipmentTimes[match.equipmentID];
            }
        }

        const startTime = Math.max(workerStart, equipStart);
        const finishTime = startTime + task.prep_time;

        workerTimes[selectedWorker] = finishTime;
        if (selectedEquip) {
            equipmentTimes[selectedEquip.equipmentID] = finishTime;
            selectedEquip.status = `W${selectedWorker}:製作#${task.id}`;
        }

        return {
            ...task,
            worker_id: parseInt(selectedWorker),
            expected_at: finishTime,
            equipment_id: selectedEquip ? selectedEquip.equipmentID : null,
            equipment_name: selectedEquip ? selectedEquip.name : null
        };
    });

    appState.pendingQueue = scheduledQueue;
    renderQueue();
    renderEquipmentAndWorkers();
}

// ==========================================
// 11. BOM, Recipes, Safety Stock & SQL display
// ==========================================

function renderCompletedOrders() {
    const list = document.getElementById("completed-orders-list");
    if (!list) return;
    
    if (appState.completedOrders.length === 0) {
        list.innerHTML = `<div class="empty-state">尚無已完成的訂單</div>`;
        return;
    }
    
    list.innerHTML = appState.completedOrders.map(order => `
        <div class="completed-item" style="display: flex; justify-content: space-between; align-items: center; padding: 10px; margin-bottom: 8px; background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.05); border-radius: 8px;">
            <div>
                <span class="order-id" style="font-weight: bold; color: var(--color-success);">#${order.id}</span>
                <span class="order-name" style="margin-left: 8px; color: var(--text-primary);">${order.content}</span>
            </div>
            <span class="completed-time" style="font-size: 12px; color: var(--text-secondary);">${order.time}</span>
        </div>
    `).join("");
}

function initBOMSubTabs() {
    const tabBtns = document.querySelectorAll(".bom-subtab-btn");
    const panels = document.querySelectorAll(".bom-panel");
    
    tabBtns.forEach(btn => {
        btn.addEventListener("click", () => {
            const targetPanelId = btn.getAttribute("data-bom-tab");
            
            tabBtns.forEach(b => b.classList.remove("active"));
            panels.forEach(p => p.classList.remove("active"));
            
            btn.classList.add("active");
            document.getElementById(targetPanelId).classList.add("active");
            
            // Auto trigger specific loads if needed when tab clicked
            if (targetPanelId === "bom-panel-recipe") {
                loadRecipeMealSelect();
            } else if (targetPanelId === "bom-panel-stock") {
                loadSafetyStock();
                loadConsumptionDates();
            }
        });
    });
}

function initBOMFunctionality() {
    // Recipes Sub-Panel
    const btnLoadRecipes = document.getElementById("btn-load-recipes");
    if (btnLoadRecipes) {
        btnLoadRecipes.addEventListener("click", () => loadRecipeMealSelect(true));
    }
    const selectRecipeMeal = document.getElementById("recipe-meal-select");
    if (selectRecipeMeal) {
        selectRecipeMeal.addEventListener("change", (e) => {
            const mealName = e.target.value;
            loadRecipeSteps(mealName);
        });
    }

    // BOM Sub-Panel
    const btnBomMeal = document.getElementById("btn-bom-meal");
    const btnBomCombo = document.getElementById("btn-bom-combo");
    if (btnBomMeal) {
        btnBomMeal.addEventListener("click", () => loadSingleMealBOM());
    }
    if (btnBomCombo) {
        btnBomCombo.addEventListener("click", () => loadComboBOMExplosion());
    }

    // Safety Stock Sub-Panel
    const btnLoadStock = document.getElementById("btn-load-stock");
    if (btnLoadStock) {
        btnLoadStock.addEventListener("click", () => loadSafetyStock());
    }
    const btnLoadConsumption = document.getElementById("btn-load-consumption");
    if (btnLoadConsumption) {
        btnLoadConsumption.addEventListener("click", () => {
            const dateSelect = document.getElementById("consumption-date-select");
            if (dateSelect && dateSelect.value) {
                loadConsumptionReport(dateSelect.value);
            } else {
                alert("請先選擇歷史日期！");
            }
        });
    }
}

async function loadRecipeMealSelect(forceRefresh = false) {
    const select = document.getElementById("recipe-meal-select");
    if (!select) return;
    
    // Check if recipes are cached
    if (appState.recipes.length === 0 || forceRefresh) {
        try {
            const res = await fetch(ENDPOINTS.recipes);
            const data = await res.json();
            
            // The get_recipes.php returns details of steps.
            // We group them into recipe objects matching Java format.
            const steps = data.data || [];
            
            // Get meals to resolve names properly
            if (appState.meals.length === 0) {
                await loadMealsList();
            }
            
            const mealMap = {};
            appState.meals.forEach(m => {
                mealMap[m.meal_id || m.mealID] = m.meal_name;
            });
            
            const grouped = {};
            steps.forEach(row => {
                const mealId = row.mealID || row.meal_id || -1;
                const mealName = row.meal_name || row.mealName || mealMap[mealId] || `餐點 ID: ${mealId}`;
                
                if (!grouped[mealName]) {
                    grouped[mealName] = {
                        meal_id: mealId,
                        meal_name: mealName,
                        recipe_name: row.recipe_name || `${mealName} 配方`,
                        steps: []
                    };
                }
                
                grouped[mealName].steps.push({
                    step_order: parseInt(row.stepOrder || row.step_order || 1),
                    step_name: row.stepDescription || row.step_description || row.step_name || row.description || "製作步驟",
                    duration_sec: row.timeMinutes ? parseInt(row.timeMinutes) * 3 : parseInt(row.duration_sec || 0),
                    equipment_type: (row.etype || row.equipment_type || "").trim().toLowerCase()
                });
            });
            
            appState.recipes = Object.values(grouped).map(r => {
                r.steps.sort((a, b) => a.step_order - b.step_order);
                return r;
            });
        } catch (err) {
            console.error("Failed to load recipes", err);
            // Mock recipes fallback
            appState.recipes = [
                {
                    meal_name: "大麥克",
                    recipe_name: "大麥克 標準",
                    steps: [
                        { step_order: 1, step_name: "拿取麵包與生菜", duration_sec: 6, equipment_type: "prep_station" },
                        { step_order: 2, step_name: "煎肉餅", duration_sec: 30, equipment_type: "grill" },
                        { step_order: 3, step_name: "組裝", duration_sec: 15, equipment_type: "plating_station" }
                    ]
                },
                {
                    meal_name: "薯條",
                    recipe_name: "薯條 快速",
                    steps: [
                        { step_order: 1, step_name: "放入炸籃", duration_sec: 6, equipment_type: "fryer" },
                        { step_order: 2, step_name: "炸制", duration_sec: 18, equipment_type: "fryer" },
                        { step_order: 3, step_name: "調味並裝盒", duration_sec: 9, equipment_type: "plating_station" }
                    ]
                }
            ];
        }
    }
    
    // Populate select
    let html = '<option value="">-- 請選擇餐點 --</option>';
    appState.recipes.forEach(r => {
        html += `<option value="${r.meal_name}">${r.meal_name}</option>`;
    });
    select.innerHTML = html;
}

function loadRecipeSteps(mealName) {
    const container = document.getElementById("recipe-steps-container");
    const sqlBadge = document.getElementById("sql-recipe");
    if (!container) return;
    
    if (sqlBadge) {
        sqlBadge.textContent = `SQL: SQL: SELECT * FROM McOS_recipe AS r JOIN McOS_meal AS m ON r.mealID=m.meal_id WHERE m.meal_name = '${mealName}' ORDER BY stepOrder;`;
        sqlBadge.style.display = "block";
    }
    
    if (!mealName) {
        container.innerHTML = `<div class="empty-state">請選擇餐點或點擊「載入食譜」</div>`;
        return;
    }
    
    const recipe = appState.recipes.find(r => r.meal_name === mealName);
    if (!recipe || !recipe.steps || recipe.steps.length === 0) {
        container.innerHTML = `<div class="empty-state" style="color: var(--color-danger);">該餐點目前無對應食譜步驟資料</div>`;
        return;
    }
    
    let html = `
        <div style="margin-bottom: 15px; border-bottom: 1px solid rgba(255,255,255,0.08); padding-bottom: 8px;">
            <strong style="color: var(--color-success); font-size: 16px;">${recipe.recipe_name || recipe.meal_name}</strong>
        </div>
        <div class="steps-flow" style="display: flex; flex-direction: column; gap: 10px;">
    `;
    
    recipe.steps.forEach(step => {
        const equipLabel = step.equipment_type ? `[設備: ${step.equipment_type.toUpperCase()}]` : '[無需設備]';
        html += `
            <div class="step-card" style="display: flex; gap: 12px; align-items: flex-start; padding: 12px; background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.05); border-radius: 8px;">
                <div class="step-badge" style="background: var(--color-primary); color: var(--color-bg); font-weight: bold; border-radius: 50%; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 13px;">${step.step_order}</div>
                <div style="flex: 1;">
                    <div style="color: var(--text-primary); font-weight: 600; font-size: 14px;">${step.step_name}</div>
                    <div style="font-size: 12px; color: var(--text-secondary); margin-top: 4px; display: flex; gap: 10px;">
                        <span>⏳ 時間: ${step.duration_sec} 秒</span>
                        <span style="color: var(--color-info); font-weight: bold;">🔧 ${equipLabel}</span>
                    </div>
                </div>
            </div>
        `;
    });
    
    html += `</div>`;
    container.innerHTML = html;
}

async function loadSingleMealBOM() {
    const container = document.getElementById("bom-tree-output");
    const sqlBadge = document.getElementById("sql-bom");
    if (!container) return;
    
    if (sqlBadge) {
        sqlBadge.textContent = `SQL=
  SELECT 
    \`m\`.\`meal_name\` AS \`餐點名稱\`,
    GROUP_CONCAT(CONCAT(\`i\`.\`ing_name\`, ' x ', \`mc\`.\`qty\`, \`i\`.\`unit\`) SEPARATOR '、') AS \`標準配方與用量\` 
  FROM ((\`McOS_meal\` \`m\` 
  JOIN \`McOS_mealCost\` \`mc\` ON ((\`m\`.\`meal_id\` = \`mc\`.\`mealID\`))) 
  JOIN \`McOS_ingredients\` \`i\` ON ((\`mc\`.\`ingID\` = \`i\`.\`ing_id\`))) 
  GROUP BY \`m\`.\`meal_id\`
`;
        sqlBadge.style.display = "block";
    }
    
    container.innerHTML = `<div class="empty-state">⏳ 正在載入單品材料配方中...</div>`;
    
    try {
        const [bomRes, ingRes] = await Promise.all([
            fetch(ENDPOINTS.mealcost),
            fetch(ENDPOINTS.ingredients)
        ]);
        
        const bomData = await bomRes.json();
        const ingData = await ingRes.json();
        
        const bomRows = bomData.data || [];
        const ingRows = ingData.data || [];
        
        // Build maps
        const ingMap = {};
        ingRows.forEach(ing => {
            ingMap[ing.ing_id || ing.ingID] = {
                name: ing.ing_name || "未知原料",
                unit: ing.unit || "個"
            };
        });
        
        if (appState.meals.length === 0) {
            await loadMealsList();
        }
        
        const mealMap = {};
        appState.meals.forEach(m => {
            mealMap[m.meal_id || m.mealID] = m.meal_name;
        });
        
        // Group by meal ID
        const grouped = {};
        bomRows.forEach(row => {
            const mid = row.mealID || row.meal_id;
            const iid = row.ingID || row.ing_id;
            const qty = parseFloat(row.qty || row.quantity || 0);
            
            if (!grouped[mid]) {
                grouped[mid] = {
                    name: mealMap[mid] || `餐點 ID: ${mid}`,
                    materials: []
                };
            }
            
            const ingInfo = ingMap[iid] || { name: `原料 ID: ${iid}`, unit: "單位" };
            grouped[mid].materials.push({
                name: ingInfo.name,
                qty: qty,
                unit: ingInfo.unit
            });
        });
        
        let html = `<div style="font-family: monospace; white-space: pre; line-height: 1.6; font-size: 13px; color: var(--text-primary);">`;
        html += `🌲 <strong>McOS 標準單品材料清單結構樹 (Single BOM)</strong><br>`;
        html += `===========================================================<br>`;
        
        const sortedMeals = Object.entries(grouped).sort((a, b) => parseInt(a[0]) - parseInt(b[0]));
        sortedMeals.forEach(([mid, data]) => {
            html += `<span style="color: var(--color-success);">├── 🍔 ${data.name} (ID: ${mid})</span><br>`;
            data.materials.forEach((mat, idx) => {
                const branch = idx === data.materials.length - 1 ? "│   └── " : "│   ├── ";
                html += `<span style="color: var(--text-secondary);">${branch}[原料] ${mat.name} * ${mat.qty} ${mat.unit}</span><br>`;
            });
        });
        
        html += `</div>`;
        container.innerHTML = html;
    } catch (err) {
        console.error("Failed to build meal BOM", err);
        container.innerHTML = `<div class="empty-state" style="color: var(--color-danger);">載入單品材料配方失敗，伺服器連線異常</div>`;
    }
}

async function loadComboBOMExplosion() {
    const container = document.getElementById("bom-tree-output");
    const sqlBadge = document.getElementById("sql-bom");
    if (!container) return;
    
    if (sqlBadge) {
        sqlBadge.textContent = `SQL=
        SELECT 
            \`c\`.\`comboID\` AS \`套餐編號\`,
            \`c\`.\`comboName\` AS \`套餐名稱\`,
            
            -- 1. 串接內含單品（對應圖中的黃色漢堡圖示清單）
            GROUP_CONCAT(
            DISTINCT CONCAT(\`m\`.\`meal_name\`, ' * ', \`cd\`.\`quantity\`) 
            SEPARATOR '、'
            ) AS \`內含單品\`,
            
            -- 2. 串接總原物料需求（對應圖中的綠色字體）
            \`total_ing\`.\`總原物料需求\`
            
        FROM \`a0303\`.\`McOS_comboMeals_new_new\` \`c\`
        
        -- 連接單品明細與單品名稱表（已修正欄位為 meal_id）
        LEFT JOIN \`a0303\`.\`McOS_comboDetail_new_new\` \`cd\` ON \`c\`.\`comboID\` = \`cd\`.\`comboID\`
        LEFT JOIN \`a0303\`.\`McOS_meal\` \`m\` ON \`cd\`.\`mealID\` = \`m\`.\`meal_id\`
        
        -- 連接總原料計算子查詢
        LEFT JOIN (
            SELECT 
            \`sub_cd\`.\`comboID\`,
            GROUP_CONCAT(
                CONCAT(\`i\`.\`ing_name\`, ' x', \`sub_total\`.\`total_qty\`, \`i\`.\`unit\`) 
                SEPARATOR '、'
            ) AS \`總原物料需求\`
            FROM \`a0303\`.\`McOS_comboDetail_new_new\` \`sub_cd\`
            JOIN \`a0303\`.\`McOS_mealCost\` \`mc\` ON \`sub_cd\`.\`mealID\` = \`mc\`.\`mealID\`
            JOIN (
            SELECT 
                \`cd2\`.\`comboID\`, 
                \`mc2\`.\`ingID\`, 
                SUM((\`cd2\`.\`quantity\` * \`mc2\`.\`qty\`)) AS \`total_qty\`
            FROM \`a0303\`.\`McOS_comboDetail_new_new\` \`cd2\`
            JOIN \`a0303\`.\`McOS_mealCost\` \`mc2\` ON \`cd2\`.\`mealID\` = \`mc2\`.\`mealID\`
            GROUP BY \`cd2\`.\`comboID\`, \`mc2\`.\`ingID\`
            ) \`sub_total\` ON \`sub_cd\`.\`comboID\` = \`sub_total\`.\`comboID\` AND \`mc\`.\`ingID\` = \`sub_total\`.\`ingID\`
            JOIN \`a0303\`.\`McOS_ingredients\` \`i\` ON \`mc\`.\`ingID\` = \`i\`.\`ing_id\`
            GROUP BY \`sub_cd\`.\`comboID\`
        ) \`total_ing\` ON \`c\`.\`comboID\` = \`total_ing\`.\`comboID\`
        
        GROUP BY \`c\`.\`comboID\`, \`c\`.\`comboName\`, \`total_ing\`.\`總原物料需求\`
        ORDER BY \`c\`.\`comboID\`
        `;
        sqlBadge.style.display = "block";
    }
    
    container.innerHTML = `<div class="empty-state">⏳ 正在進行二級 BOM 聯動爆炸解析 (Combo Explosion) ...</div>`;
    
    try {
        const res = await fetch(ENDPOINTS.combo_explosion);
        const result = await res.json();
        
        if (result.status !== "success") {
            container.innerHTML = `<div class="empty-state" style="color: var(--color-danger);">載入失敗: ${result.message || "未知錯誤"}</div>`;
            return;
        }
        
        const details = result.details || [];
        const boms = result.boms || [];
        
        // Build map for quick access
        const bomMap = {};
        boms.forEach(b => {
            bomMap[b.comboID] = b.total_ingredients;
        });
        
        // Group details by comboID
        const groupedCombos = {};
        details.forEach(row => {
            const cid = row.comboID;
            const cname = row.comboName;
            const mname = row.meal_name;
            const qty = row.quantity || 1;
            
            if (!groupedCombos[cid]) {
                groupedCombos[cid] = {
                    name: cname,
                    meals: []
                };
            }
            groupedCombos[cid].meals.push({
                name: mname,
                qty: qty
            });
        });
        
        let html = `<div style="font-family: monospace; white-space: pre; line-height: 1.6; font-size: 13px; color: var(--text-primary);">`;
        html += `🌴 <strong>[二級跨表聯動] 套餐 ➔ 內含單品 ➔ 最底層物料關聯圖 (Combo Explosion)</strong><br>`;
        html += `===========================================================<br>`;
        
        const sortedCombos = Object.entries(groupedCombos).sort((a, b) => parseInt(a[0]) - parseInt(b[0]));
        sortedCombos.forEach(([cid, data]) => {
            html += `<span style="color: var(--color-info);">🍱 套餐：${data.name} (Combo ID: ${cid})</span><br>`;
            data.meals.forEach(meal => {
                html += `  ├── 🍔 ${meal.name} * ${meal.qty}<br>`;
            });
            const matList = bomMap[cid] || "無底層配方資料";
            html += `  <span style="color: var(--color-success);">➔ 總累計原料需求：${matList}</span><br>`;
            html += `-----------------------------------------------------------<br>`;
        });
        
        html += `📊 <strong>[本系統全套餐原始物料爆炸清單清查完畢]</strong>`;
        html += `</div>`;
        container.innerHTML = html;
    } catch (err) {
        console.error("Failed to explosion combo BOM", err);
        container.innerHTML = `<div class="empty-state" style="color: var(--color-danger);">載入套餐物料爆炸圖失敗，伺服器連線異常</div>`;
    }
}

async function loadSafetyStock() {
    const tableBody = document.querySelector("#stock-table tbody");
    const sqlBadge = document.getElementById("sql-stock");
    if (!tableBody) return;
    
    if (sqlBadge) {
        sqlBadge.textContent = `SQL=
  SELECT 
    \`i\`.\`ing_id\` AS \`原料編號\`,
    \`i\`.\`ing_name\` AS \`原料名稱\`,
    \`i\`.\`stock_qty\` AS \`目前庫存量\`,
    \`s\`.\`safe_qty\` AS \`安全庫存標準\`,
    \`i\`.\`unit\` AS \`單位\`,
    (CASE 
      WHEN (\`i\`.\`stock_qty\` <= (\`s\`.\`safe_qty\` * 0.5)) THEN '🔴 庫存極低，請緊急採購' 
      WHEN (\`i\`.\`stock_qty\` <= \`s\`.\`safe_qty\`) THEN '🟡 低於安全標準，請注意' 
      ELSE '✅ 庫存充足' 
    END) AS \`庫存狀態\` 
  FROM (\`McOS_ingredients\` \`i\` 
  JOIN \`McOS_safetyStock\` \`s\` ON ((\`i\`.\`ing_id\` = \`s\`.\`ingID\`))) 
  ORDER BY (\`i\`.\`stock_qty\` / \`s\`.\`safe_qty\`)
`;
        sqlBadge.style.display = "block";
    }
    
    tableBody.innerHTML = `<tr><td colspan="5" class="empty-state">⏳ 正在載入庫存數據...</td></tr>`;
    
    try {
        const res = await fetch(ENDPOINTS.ingredients);
        const result = await res.json();
        const ingredients = result.data || [];
        
        if (ingredients.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="5" class="empty-state">目前無原物料庫存資料</td></tr>`;
            return;
        }
        
        let html = "";
        ingredients.forEach(ing => {
            const qty = parseFloat(ing.stock_qty || 0);
            
            // Heuristic safety stock limit (e.g. 100 for pieces/cans/bags, 500 for grams/mls)
            let limit = 200;
            const unit = ing.unit || "";
            if (unit === "克" || unit === "毫升") {
                limit = 2000; 
            }
            
            let badgeClass = "badge-success";
            let statusText = "🟢 存量充足";
            
            if (qty <= 0) {
                badgeClass = "badge-danger";
                statusText = "🚨 斷貨預警 (0)";
            } else if (qty < limit * 0.5) {
                badgeClass = "badge-danger";
                statusText = "🚨 水位極低 (過低)";
            } else if (qty < limit) {
                badgeClass = "badge-warning";
                statusText = "⚠️ 水位偏低 (警報)";
            }
            
            html += `
                <tr>
                    <td>${ing.ing_id || ing.ingID}</td>
                    <td><strong>${ing.ing_name}</strong></td>
                    <td>${ing.unit}</td>
                    <td>${qty.toLocaleString()}</td>
                    <td><span class="bom-badge ${badgeClass}">${statusText}</span></td>
                </tr>
            `;
        });
        
        tableBody.innerHTML = html;
    } catch (err) {
        console.error("Failed to load safety stock", err);
        tableBody.innerHTML = `<tr><td colspan="5" class="empty-state" style="color: var(--color-danger);">載入庫存失敗，API 連線異常</td></tr>`;
    }
}

async function loadConsumptionDates() {
    const select = document.getElementById("consumption-date-select");
    if (!select) return;
    
    select.innerHTML = `<option value="">載入日期...</option>`;
    
    try {
        const res = await fetch(ENDPOINTS.available_dates);
        const dates = await res.json();
        
        if (dates && Array.isArray(dates) && dates.length > 0) {
            let html = '<option value="">-- 選擇日期 --</option>';
            dates.forEach(d => {
                html += `<option value="${d}">${d}</option>`;
            });
            select.innerHTML = html;
        } else {
            select.innerHTML = `<option value="">尚無有效日期</option>`;
        }
    } catch (err) {
        console.error("Failed to load available dates", err);
        select.innerHTML = `<option value="">連線錯誤</option>`;
    }
}

async function loadConsumptionReport(date) {
    const tableBody = document.querySelector("#consumption-table tbody");
    const sqlBadge = document.getElementById("sql-stock");
    if (!tableBody) return;
    
    if (sqlBadge) {
        sqlBadge.textContent = `SQL: SELECT 原料名稱, 單日總消耗數量, 單位
FROM View_Daily_Total_Consumption
WHERE 日期 = '${date}';`;
        sqlBadge.style.display = "block";
    }
    
    tableBody.innerHTML = `<tr><td colspan="3" class="empty-state">⏳ 正在計算 ${date} 原物料消耗...</td></tr>`;
    
    try {
        const res = await fetch(`${ENDPOINTS.consumption_report}?date=${date}`);
        const result = await res.json();
        const data = Array.isArray(result) ? result : (result.data || []);
        
        if (data.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="3" class="empty-state">⚠️ 該日期 (${date}) 無消耗數據紀錄</td></tr>`;
            return;
        }
        
        // Filter elements of dataType == "consumption"
        const consumptions = data.filter(r => r.dataType === "consumption");
        
        if (consumptions.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="3" class="empty-state">⚠️ 該日期 (${date}) 無連動原物料消耗紀錄</td></tr>`;
            return;
        }
        
        let html = "";
        consumptions.forEach(row => {
            html += `
                <tr>
                    <td><strong>${row.原料名稱}</strong></td>
                    <td>${parseFloat(row.單日總消耗數量 || 0).toLocaleString()}</td>
                    <td>${row.單位}</td>
                </tr>
            `;
        });
        
        tableBody.innerHTML = html;
    } catch (err) {
        console.error("Failed to load consumption report", err);
        tableBody.innerHTML = `<tr><td colspan="3" class="empty-state" style="color: var(--color-danger);">載入消耗分析失敗，API 連線異常</td></tr>`;
    }
}
