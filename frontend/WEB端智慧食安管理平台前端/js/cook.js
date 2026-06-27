/**
 * cook.js — 烹饪记录管理
 *
 * ===== 运行验证说明 =====
 *  双击 index.html → 点击左侧「后厨管理 / 烹饪记录」导航进入版块
 *
 *  【① 生成烹饪任务】
 *  - 点击右上角「生成烹饪任务」→ 弹出"选择菜谱计划"弹窗
 *    仅展示「已审核」状态的菜谱计划，支持按计划名称/门店筛选
 *  - 选择一条计划后点击「确认生成」→ 按菜谱清单每行生成一条烹饪任务
 *    食材明细联动菜谱 ingredients × 供应份数，默认备料完成=否
 *  - 生成成功后提示"烹饪任务生成成功，共生成 X 条任务"，列表自动刷新
 *
 *  【② 查看烹饪任务列表】
 *  - 列表含：任务编号/关联计划/菜谱名称/门店/份数/负责人/计划时段/状态
 *  - 筛选：任务编号/菜谱名称/门店/状态/烹饪日期范围
 *  - 分页10条，无数据时显示引导提示
 *  - 状态：待烹饪（蓝）/ 烹饪中（黄）/ 已完成（绿）/ 已取消（灰）
 *
 *  【③ 查看烹饪任务详情】
 *  - 点击「详情」或任务编号链接 → 只读弹窗：基础信息区 + 食材明细区
 *
 * ===== 核心改动点 =====
 *  1. 新建 js/cook.js（本文件）
 *  2. mock-data.js 追加 cookTasks 数组（5条，含 Mock 厨师字段）
 *  3. sidebar.js renderPage() 追加 cook → renderCookPage() 分支
 *  4. index.html 追加 <script src="js/cook.js">
 *  5. 生成逻辑：从 recipePlans(approved) → items → recipes.ingredients 三层联动构建 cookTask
 */

/* ============================================================
   常量
   ============================================================ */
const CT_STATUS_MAP = {
    pending:  { label: '待烹饪', cls: 'tag-primary'  },
    cooking:  { label: '烹饪中', cls: 'tag-warning'  },
    done:     { label: '已完成', cls: 'tag-success'  },
    cancelled:{ label: '已取消', cls: 'tag-info'     }
};

const CT_MEAL_MAP = {
    breakfast: '早餐',
    lunch:     '午餐',
    dinner:    '晚餐',
    night:     '夜宵'
};

/* Mock 厨师列表 */
const MOCK_CHEFS = [
    { id: 1, name: '赵厨长' },
    { id: 2, name: '钱仓管' },
    { id: 3, name: '孙大厨' },
    { id: 4, name: '李助厨' }
];

/* ============================================================
   状态变量
   ============================================================ */
window.ctPage         = 1;
window.ctPageSize     = 10;
window.ctFilteredList = [];

/* 生成弹窗内部筛选列表 */
window._ctPlanFilteredList = [];

/* ============================================================
   渲染入口
   ============================================================ */
function renderCookPage(container) {
    const orgNames = [...new Set((window.mockData.cookTasks || []).map(t => t.orgName).filter(Boolean))];
    const orgOpts  = orgNames.map(n => `<option value="${n}">${n}</option>`).join('');

    container.innerHTML = `
        <div class="toolbar">
            <div class="toolbar-row">
                <input class="form-input" id="ctSearchNo"     placeholder="任务编号"   style="width:155px">
                <input class="form-input" id="ctSearchRecipe" placeholder="菜谱名称"   style="width:130px">
                <input class="form-input" id="ctSearchOrg"    placeholder="适用门店"   style="width:130px">
                <select class="form-select" id="ctSearchStatus" style="width:110px">
                    <option value="">全部状态</option>
                    <option value="pending">待烹饪</option>
                    <option value="cooking">烹饪中</option>
                    <option value="done">已完成</option>
                    <option value="cancelled">已取消</option>
                </select>
                <input type="date" class="form-input" id="ctSearchDateFrom" style="width:140px" title="烹饪日期起">
                <input type="date" class="form-input" id="ctSearchDateTo"   style="width:140px" title="烹饪日期止">
                <button class="btn btn-primary" onclick="searchCookTask()">搜索</button>
                <button class="btn btn-default" onclick="resetCookTaskSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openSelectPlanModal()">＋ 生成烹饪任务</button>
            </div>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>任务编号</th>
                        <th>关联计划</th>
                        <th>菜谱名称</th>
                        <th>适用门店</th>
                        <th>计划份数</th>
                        <th>烹饪负责人</th>
                        <th>计划时段</th>
                        <th>烹饪日期</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="ctTableBody"></tbody>
            </table>
            <div class="pagination" id="ctPagination"></div>
        </div>`;

    _filterCookTask();
}

/* ============================================================
   筛选 & 分页
   ============================================================ */
function _filterCookTask() {
    const no       = (document.getElementById('ctSearchNo')?.value      || '').trim().toLowerCase();
    const recipe   = (document.getElementById('ctSearchRecipe')?.value  || '').trim().toLowerCase();
    const org      = (document.getElementById('ctSearchOrg')?.value     || '').trim().toLowerCase();
    const status   = (document.getElementById('ctSearchStatus')?.value  || '');
    const dateFrom = (document.getElementById('ctSearchDateFrom')?.value || '');
    const dateTo   = (document.getElementById('ctSearchDateTo')?.value   || '');

    window.ctFilteredList = (window.mockData.cookTasks || []).filter(t => {
        if (no     && !t.taskNo.toLowerCase().includes(no))                        return false;
        if (recipe && !t.recipeName.toLowerCase().includes(recipe))                return false;
        if (org    && !(t.orgName || '').toLowerCase().includes(org))              return false;
        if (status && t.status !== status)                                         return false;
        if (dateFrom && t.cookDate < dateFrom)                                     return false;
        if (dateTo   && t.cookDate > dateTo)                                       return false;
        return true;
    });

    window.ctPage = 1;
    _renderCookTaskRows();
}

function searchCookTask()      { _filterCookTask(); }
function resetCookTaskSearch() {
    ['ctSearchNo','ctSearchRecipe','ctSearchOrg','ctSearchStatus','ctSearchDateFrom','ctSearchDateTo']
        .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
    _filterCookTask();
}

function ctChangePage(p) { window.ctPage = p; _renderCookTaskRows(); }

function _renderCookTaskRows() {
    const list     = window.ctFilteredList;
    const total    = list.length;
    const pageSize = window.ctPageSize;
    const page     = window.ctPage;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const tbody = document.getElementById('ctTableBody');
    if (!tbody) return;

    if (!pageData.length) {
        const hint = total === 0 && !(window.mockData.cookTasks || []).length
            ? '暂无烹饪任务数据，可点击「生成烹饪任务」创建'
            : '暂无符合条件的烹饪任务';
        tbody.innerHTML = `<tr><td colspan="10" style="text-align:center;padding:40px;color:#909399">${hint}</td></tr>`;
        document.getElementById('ctPagination').innerHTML = '';
        return;
    }

    tbody.innerHTML = pageData.map(t => {
        const sm       = CT_STATUS_MAP[t.status] || { label: t.status, cls: 'tag-info' };
        const mealLbl  = CT_MEAL_MAP[t.mealTime] || t.mealTime || '—';

        return `<tr>
            <td><a href="javascript:void(0)" class="btn-link" onclick="showCookTaskDetail(${t.id})">${t.taskNo}</a></td>
            <td><span style="font-size:12px;color:#409eff">${t.planNo || '—'}</span></td>
            <td>${t.recipeName}</td>
            <td>${t.orgName || '—'}</td>
            <td>${t.servings} 份</td>
            <td>${t.chefName}</td>
            <td>${mealLbl}</td>
            <td>${t.cookDate}</td>
            <td><span class="tag ${sm.cls}">${sm.label}</span></td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showCookTaskDetail(${t.id})">详情</button>
                </div>
            </td>
        </tr>`;
    }).join('');

    const totalPages = Math.ceil(total / pageSize);
    let pgHtml = `<span style="color:#606266;font-size:14px">共 ${total} 条</span>`;
    if (totalPages > 1) {
        pgHtml += ` <button class="btn btn-default" ${page<=1?'disabled':''} onclick="ctChangePage(${page-1})">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            pgHtml += `<button class="btn ${i===page?'btn-primary':'btn-default'}" onclick="ctChangePage(${i})">${i}</button>`;
        }
        pgHtml += `<button class="btn btn-default" ${page>=totalPages?'disabled':''} onclick="ctChangePage(${page+1})">下一页</button>`;
    }
    document.getElementById('ctPagination').innerHTML = pgHtml;
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showCookTaskDetail(id) {
    const t = (window.mockData.cookTasks || []).find(x => x.id === id);
    if (!t) return;
    const sm      = CT_STATUS_MAP[t.status] || { label: t.status, cls: 'tag-info' };
    const mealLbl = CT_MEAL_MAP[t.mealTime] || t.mealTime || '—';

    const ingRows = (t.ingredients || []).map(ing => `
        <tr>
            <td>${ing.materialName}</td>
            <td>${ing.spec || '—'}</td>
            <td>${ing.unit}</td>
            <td>${ing.needQty} ${ing.unit}</td>
            <td>
                <span class="tag ${ing.prepDone ? 'tag-success' : 'tag-warning'}">
                    ${ing.prepDone ? '已备料' : '未备料'}
                </span>
            </td>
            <td>${ing.prepRemark || '—'}</td>
        </tr>`).join('');

    const html = `
        <div class="modal-header">
            <span class="modal-title">${t.taskNo} - 烹饪任务详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基础信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">任务编号</span><span class="detail-value">${t.taskNo}</span></div>
                    <div class="detail-item"><span class="detail-label">烹饪状态</span><span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span></div>
                    <div class="detail-item"><span class="detail-label">关联菜谱计划</span><span class="detail-value" style="color:#409eff">${t.planNo || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">菜谱名称</span><span class="detail-value">${t.recipeName}</span></div>
                    <div class="detail-item"><span class="detail-label">适用门店</span><span class="detail-value">${t.orgName || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">计划供应份数</span><span class="detail-value">${t.servings} 份</span></div>
                    <div class="detail-item"><span class="detail-label">烹饪负责人</span><span class="detail-value">${t.chefName}</span></div>
                    <div class="detail-item"><span class="detail-label">计划烹饪时段</span><span class="detail-value">${mealLbl}</span></div>
                    <div class="detail-item"><span class="detail-label">烹饪日期</span><span class="detail-value">${t.cookDate}</span></div>
                    <div class="detail-item"><span class="detail-label">创建时间</span><span class="detail-value">${t.createdAt}</span></div>
                    ${t.remark ? `<div class="detail-item" style="grid-column:span 2"><span class="detail-label">备注</span><span class="detail-value">${t.remark}</span></div>` : ''}
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">食材明细</div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr>
                            <th>物料名称</th><th>规格</th><th>单位</th>
                            <th>需用数量</th><th>备料状态</th><th>备料备注</th>
                        </tr></thead>
                        <tbody>${ingRows || '<tr><td colspan="6" style="text-align:center;color:#909399">暂无食材数据</td></tr>'}</tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>`;
    showModal(html, '820px');
}

/* ============================================================
   选择菜谱计划弹窗（生成烹饪任务入口）
   ============================================================ */
function openSelectPlanModal() {
    const approvedPlans = (window.mockData.recipePlans || []).filter(p => p.status === 'approved');
    window._ctPlanFilteredList = approvedPlans.slice();

    const html = `
        <div class="modal-header">
            <span class="modal-title">选择菜谱计划 — 生成烹饪任务</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div style="display:flex;gap:10px;margin-bottom:16px;flex-wrap:wrap">
                <input class="form-input" id="ctPlanSearchName" placeholder="计划名称"   style="width:200px">
                <input class="form-input" id="ctPlanSearchOrg"  placeholder="适用门店"   style="width:160px">
                <button class="btn btn-primary" onclick="_filterSelectablePlans()">筛选</button>
                <button class="btn btn-default" onclick="_resetSelectablePlans()">重置</button>
            </div>
            <div style="font-size:12px;color:#909399;margin-bottom:10px">
                仅展示「已审核」状态的菜谱计划，点击行选中
            </div>
            <div style="overflow-x:auto;max-height:360px;overflow-y:auto">
                <table>
                    <thead><tr style="background:#f5f7fa">
                        <th style="width:36px"></th>
                        <th>计划单号</th>
                        <th>计划名称</th>
                        <th>适用门店</th>
                        <th>计划周期</th>
                        <th>计划日期</th>
                        <th>菜谱数</th>
                    </tr></thead>
                    <tbody id="ctPlanTableBody"></tbody>
                </table>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="_doGenCookTasks()" style="margin-left:8px">确认生成</button>
        </div>`;

    showModal(html, '780px');
    _renderSelectablePlans();
}

const MP_PERIOD_LABEL = { day: '日', week: '周', month: '月' };

function _renderSelectablePlans() {
    const tbody = document.getElementById('ctPlanTableBody');
    if (!tbody) return;
    const list = window._ctPlanFilteredList || [];

    if (!list.length) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:24px;color:#909399">
            暂无已审核的菜谱计划</td></tr>`;
        return;
    }

    tbody.innerHTML = list.map(p => {
        const periodLbl = MP_PERIOD_LABEL[p.period] || p.period;
        return `<tr id="ctPlanRow_${p.id}" style="cursor:pointer"
                    onclick="_selectPlanRow(${p.id})"
                    onmouseover="this.style.background='#f0f9eb'" onmouseout="_restorePlanRowBg(${p.id})">
            <td style="text-align:center">
                <input type="radio" name="ctPlanRadio" value="${p.id}" id="ctPlanRadio_${p.id}"
                       onclick="event.stopPropagation();_selectPlanRow(${p.id})">
            </td>
            <td style="color:#409eff;font-size:13px">${p.planNo}</td>
            <td>${p.planName}</td>
            <td>${p.orgName}</td>
            <td>${periodLbl}</td>
            <td>${p.planDate}</td>
            <td>${(p.items||[]).length} 个</td>
        </tr>`;
    }).join('');
}

function _selectPlanRow(planId) {
    // 高亮选中行
    document.querySelectorAll('[id^="ctPlanRow_"]').forEach(tr => {
        tr.style.background = '';
    });
    const row = document.getElementById(`ctPlanRow_${planId}`);
    if (row) row.style.background = '#ecf5ff';
    const radio = document.getElementById(`ctPlanRadio_${planId}`);
    if (radio) radio.checked = true;
}

function _restorePlanRowBg(planId) {
    const radio = document.getElementById(`ctPlanRadio_${planId}`);
    const row   = document.getElementById(`ctPlanRow_${planId}`);
    if (row && !(radio && radio.checked)) row.style.background = '';
}

function _filterSelectablePlans() {
    const name = (document.getElementById('ctPlanSearchName')?.value || '').trim().toLowerCase();
    const org  = (document.getElementById('ctPlanSearchOrg')?.value  || '').trim().toLowerCase();
    const approved = (window.mockData.recipePlans || []).filter(p => p.status === 'approved');
    window._ctPlanFilteredList = approved.filter(p => {
        if (name && !p.planName.toLowerCase().includes(name)) return false;
        if (org  && !p.orgName.toLowerCase().includes(org))  return false;
        return true;
    });
    _renderSelectablePlans();
}

function _resetSelectablePlans() {
    const nameEl = document.getElementById('ctPlanSearchName');
    const orgEl  = document.getElementById('ctPlanSearchOrg');
    if (nameEl) nameEl.value = '';
    if (orgEl)  orgEl.value  = '';
    window._ctPlanFilteredList = (window.mockData.recipePlans || []).filter(p => p.status === 'approved');
    _renderSelectablePlans();
}

/* ============================================================
   确认生成烹饪任务
   ============================================================ */
function _doGenCookTasks() {
    // 读取选中的 radio
    const checked = document.querySelector('input[name="ctPlanRadio"]:checked');
    if (!checked) {
        showToast('请先选择一条已审核的菜谱计划', 'error');
        return;
    }

    const planId = Number(checked.value);
    const plan   = (window.mockData.recipePlans || []).find(p => p.id === planId);
    if (!plan || plan.status !== 'approved') {
        showToast('仅可选择已审核的菜谱计划生成烹饪任务', 'error');
        return;
    }

    const tasks  = window.mockData.cookTasks || (window.mockData.cookTasks = []);
    const now    = _ctNow();
    const today  = _ctToday();
    let   genCnt = 0;

    // 默认厨师按序轮换
    const chefs  = MOCK_CHEFS;
    let   chefIdx = 0;

    (plan.items || []).forEach(item => {
        // 查找菜谱
        const recipe = (window.mockData.recipes || []).find(r => r.id === item.recipeId);

        // 构建食材明细
        const ingredients = (recipe ? (recipe.ingredients || []) : []).map(ing => ({
            materialId:   ing.materialId,
            materialName: ing.materialName,
            spec:         ing.spec,
            unit:         ing.unit,
            // 总需用量 = 单份用量 × 供应份数
            needQty:      (() => {
                const per = ing.quantity || 0;
                const srv = item.servings || 0;
                return Math.round(per * srv * 100) / 100;
            })(),
            prepDone:   false,
            prepRemark: ''
        }));

        const newId = tasks.length ? Math.max(...tasks.map(t => t.id)) + 1 : 1;
        tasks.push({
            id:          newId,
            taskNo:      _genCtNo(newId),
            planId:      plan.id,
            planNo:      plan.planNo,
            recipeId:    item.recipeId,
            recipeName:  item.recipeName,
            orgId:       plan.orgId,
            orgName:     plan.orgName,
            servings:    item.servings || 0,
            chefId:      chefs[chefIdx % chefs.length].id,
            chefName:    chefs[chefIdx % chefs.length].name,
            mealTime:    item.mealTime,
            cookDate:    plan.planDate || today,
            status:      'pending',
            remark:      '',
            tenantId:    1,
            createdAt:   now,
            updatedAt:   now,
            ingredients
        });

        chefIdx++;
        genCnt++;
    });

    closeModal();

    if (genCnt > 0) {
        showToast(`烹饪任务生成成功，共生成 ${genCnt} 条任务`);
        _filterCookTask();
    } else {
        showToast('该菜谱计划无菜谱条目，未生成任务', 'error');
    }
}

/* ============================================================
   工具函数
   ============================================================ */
function _genCtNo(id) {
    const d   = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const seq = String(id).padStart(3, '0');
    return `CT-${d}-${seq}`;
}

function _ctToday() { return new Date().toISOString().slice(0, 10); }

function _ctNow()   { return new Date().toLocaleString('sv-SE').replace('T', ' '); }
