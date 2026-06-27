/**
 * recipePlan.js — 菜谱计划管理
 *
 * ===== 运行验证说明 =====
 *  双击 index.html → 点击左侧「菜谱营养 / 菜谱计划」导航进入版块
 *
 *  【① 新增菜谱计划】
 *  - 点击「新增菜谱计划」→ 填写：计划名称（必填）、适用门店（必填）、计划周期、计划日期、制定人、备注
 *  - 菜谱清单（必填）：选择菜谱→计划供应份数→执行时段（早餐/午餐/晚餐/夜宵）→是否主推
 *    → 预估食材成本自动计算（菜谱 ingredients 中食材均价 × 份数，Mock 计算）
 *  - 提交后列表新增，状态默认「待审核」，提示"菜谱计划新增成功"
 *
 *  【② 列表筛选/分页】
 *  - 筛选：计划单号/计划名称/适用门店/计划周期/日期范围/状态
 *  - 默认隐藏「已删除」（通过状态筛选可查找）
 *  - 分页10条，含总条数
 *
 *  【③ 查看详情】
 *  - 点击「详情」或单号链接 → 只读弹窗，展示基本信息+审核信息（如有）+菜谱清单+食材明细汇总
 *  - 食材明细联动菜谱 ingredients，展示物料名称/规格/单位/单份用量/总需求量
 *
 *  【④ 编辑菜谱计划】
 *  - 仅「待审核」可编辑；精准回显所有字段含菜谱行；预估成本编辑后重新自动计算
 *
 *  【⑤ 审核菜谱计划】
 *  - 仅「待审核」可审核；审核通过→「已审核」（审核人自动填充）；审核驳回→「已作废」
 *
 *  【⑥ 删除菜谱计划】
 *  - 「待审核」「已作废」可删除；「已审核」置灰提示"已审核的菜谱计划不允许删除"
 *  - 二次确认后软删除
 *
 * ===== 核心改动点 =====
 *  1. 新建 js/recipePlan.js（本文件）
 *  2. mock-data.js 追加 recipePlans 数组（5条）
 *  3. sidebar.js renderPage() 追加 plan → renderRecipePlanPage() 分支
 *  4. index.html 追加 <script src="js/recipePlan.js">
 *  5. 预估食材成本计算：遍历 recipes.ingredients，查 inventorySummary.avgCostPrice × 单份用量 × 份数，汇总
 */

/* ============================================================
   常量
   ============================================================ */
const MP_STATUS_MAP = {
    pending:  { label: '待审核', cls: 'tag-warning' },
    approved: { label: '已审核', cls: 'tag-success'  },
    void:     { label: '已作废', cls: 'tag-info'     },
    deleted:  { label: '已删除', cls: 'tag-danger'   }
};

const MP_PERIOD_MAP = {
    day:   '日',
    week:  '周',
    month: '月'
};

const MP_MEAL_MAP = {
    breakfast: '早餐',
    lunch:     '午餐',
    dinner:    '晚餐',
    night:     '夜宵'
};

/* ============================================================
   状态变量
   ============================================================ */
window.mpPage         = 1;
window.mpPageSize     = 10;
window.mpFilteredList = [];
window._mpRowIdx      = 0;

/* ============================================================
   预估食材成本计算（核心 Mock 逻辑）
   - 对每个菜谱行：查 recipes[id].ingredients → 每条食材查 inventorySummary.avgCostPrice
   - estCost = Σ (食材单价 × 单份用量) × 份数
   ============================================================ */
function _calcRecipeEstCost(recipeId, servings) {
    const recipe = (window.mockData.recipes || []).find(r => r.id == recipeId);
    if (!recipe) return 0;
    let costPerServing = 0;
    (recipe.ingredients || []).forEach(ing => {
        const inv = (window.mockData.inventorySummary || []).find(s => s.materialId === ing.materialId);
        const unitPrice = inv ? inv.avgCostPrice : 5; // 默认5元兜底
        // 单位换算：忽略 g/ml 微量调味品（量 < 50 默认按 0.1 元/份）
        if (ing.unit === 'g' || ing.unit === 'ml') {
            costPerServing += (ing.quantity / 1000) * unitPrice;
        } else {
            costPerServing += ing.quantity * unitPrice;
        }
    });
    return Math.round(costPerServing * servings * 100) / 100;
}

function _calcTotalEstCost(items) {
    return items.reduce((sum, it) => {
        return sum + _calcRecipeEstCost(it.recipeId, it.servings || 0);
    }, 0);
}

/* ============================================================
   渲染入口
   ============================================================ */
function renderRecipePlanPage(container) {
    const orgNames = (window.mockData.orgs || [])
        .filter(g => g.status === 'active' && (g.orgType === 'canteen' || g.orgType === 'dept'))
        .map(g => g.orgName);
    const orgOpts = orgNames.map(n => `<option value="${n}">${n}</option>`).join('');

    container.innerHTML = `
        <div class="toolbar">
            <div class="toolbar-row">
                <input class="form-input" id="mpSearchNo"   placeholder="计划单号"   style="width:160px">
                <input class="form-input" id="mpSearchName" placeholder="计划名称"   style="width:140px">
                <select class="form-select" id="mpSearchOrg" style="width:140px">
                    <option value="">全部门店</option>
                    ${orgOpts}
                </select>
                <select class="form-select" id="mpSearchPeriod" style="width:100px">
                    <option value="">全部周期</option>
                    <option value="day">日</option>
                    <option value="week">周</option>
                    <option value="month">月</option>
                </select>
                <input type="date" class="form-input" id="mpSearchDateFrom" style="width:140px" title="计划日期起">
                <input type="date" class="form-input" id="mpSearchDateTo"   style="width:140px" title="计划日期止">
                <select class="form-select" id="mpSearchStatus" style="width:110px">
                    <option value="">全部状态</option>
                    <option value="pending">待审核</option>
                    <option value="approved">已审核</option>
                    <option value="void">已作废</option>
                    <option value="deleted">已删除</option>
                </select>
                <button class="btn btn-primary" onclick="searchRecipePlan()">搜索</button>
                <button class="btn btn-default" onclick="resetRecipePlanSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openRecipePlanModal()">＋ 新增菜谱计划</button>
            </div>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>计划单号</th>
                        <th>计划名称</th>
                        <th>适用门店</th>
                        <th>计划周期</th>
                        <th>计划日期</th>
                        <th>菜谱数量</th>
                        <th>预估总成本</th>
                        <th>制定人</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="mpTableBody"></tbody>
            </table>
            <div class="pagination" id="mpPagination"></div>
        </div>`;

    _filterRecipePlan();
}

/* ============================================================
   筛选 & 分页
   ============================================================ */
function _filterRecipePlan() {
    const no       = (document.getElementById('mpSearchNo')?.value      || '').trim().toLowerCase();
    const name     = (document.getElementById('mpSearchName')?.value    || '').trim().toLowerCase();
    const org      = (document.getElementById('mpSearchOrg')?.value     || '');
    const period   = (document.getElementById('mpSearchPeriod')?.value  || '');
    const status   = (document.getElementById('mpSearchStatus')?.value  || '');
    const dateFrom = (document.getElementById('mpSearchDateFrom')?.value || '');
    const dateTo   = (document.getElementById('mpSearchDateTo')?.value   || '');

    window.mpFilteredList = (window.mockData.recipePlans || []).filter(o => {
        if (!status && o.status === 'deleted')                              return false;
        if (no     && !o.planNo.toLowerCase().includes(no))                 return false;
        if (name   && !o.planName.toLowerCase().includes(name))             return false;
        if (org    && o.orgName !== org)                                     return false;
        if (period && o.period !== period)                                   return false;
        if (status && o.status !== status)                                   return false;
        if (dateFrom && o.planDate < dateFrom)                               return false;
        if (dateTo   && o.planDate > dateTo)                                 return false;
        return true;
    });

    window.mpPage = 1;
    _renderRecipePlanRows();
}

function searchRecipePlan()      { _filterRecipePlan(); }
function resetRecipePlanSearch() {
    ['mpSearchNo','mpSearchName','mpSearchOrg','mpSearchPeriod',
     'mpSearchStatus','mpSearchDateFrom','mpSearchDateTo']
        .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
    _filterRecipePlan();
}

function mpChangePage(p) { window.mpPage = p; _renderRecipePlanRows(); }

function _renderRecipePlanRows() {
    const list     = window.mpFilteredList;
    const total    = list.length;
    const pageSize = window.mpPageSize;
    const page     = window.mpPage;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const tbody = document.getElementById('mpTableBody');
    if (!tbody) return;

    if (!pageData.length) {
        tbody.innerHTML = `<tr><td colspan="10" style="text-align:center;padding:40px;color:#909399">暂无菜谱计划数据</td></tr>`;
        document.getElementById('mpPagination').innerHTML = '';
        return;
    }

    tbody.innerHTML = pageData.map(o => {
        const sm          = MP_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };
        const periodLabel = MP_PERIOD_MAP[o.period] || o.period || '—';
        const recipeCount = (o.items || []).length;
        const totalCost   = _calcTotalEstCost(o.items || []);

        // 编辑：仅待审核
        const canEdit = o.status === 'pending';
        const editBtn = canEdit
            ? `<button class="btn-link" onclick="openRecipePlanModal(${o.id})">编辑</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('仅待审核状态可编辑','error')">编辑</button>`;

        // 审核：仅待审核
        const canAudit = o.status === 'pending';
        const auditBtn = canAudit
            ? `<button class="btn-link" style="color:#409eff" onclick="openMpAuditModal(${o.id})">审核</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('${_mpAuditDisabledTip(o.status)}','error')">审核</button>`;

        // 删除：待审核/已作废
        const canDelete = o.status === 'pending' || o.status === 'void';
        const deleteBtn = canDelete
            ? `<button class="btn-link danger" onclick="deleteRecipePlan(${o.id})">删除</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('${_mpDeleteDisabledTip(o.status)}','error')">删除</button>`;

        return `<tr>
            <td><a href="javascript:void(0)" class="btn-link" onclick="showRecipePlanDetail(${o.id})">${o.planNo}</a></td>
            <td>${o.planName}</td>
            <td>${o.orgName}</td>
            <td>${periodLabel}</td>
            <td>${o.planDate}</td>
            <td>${recipeCount} 个</td>
            <td>¥${totalCost.toFixed(2)}</td>
            <td>${o.creatorName}</td>
            <td><span class="tag ${sm.cls}">${sm.label}</span></td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showRecipePlanDetail(${o.id})">详情</button>
                    ${editBtn}
                    ${auditBtn}
                    ${deleteBtn}
                </div>
            </td>
        </tr>`;
    }).join('');

    const totalPages = Math.ceil(total / pageSize);
    let pgHtml = `<span style="color:#606266;font-size:14px">共 ${total} 条</span>`;
    if (totalPages > 1) {
        pgHtml += ` <button class="btn btn-default" ${page<=1?'disabled':''} onclick="mpChangePage(${page-1})">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            pgHtml += `<button class="btn ${i===page?'btn-primary':'btn-default'}" onclick="mpChangePage(${i})">${i}</button>`;
        }
        pgHtml += `<button class="btn btn-default" ${page>=totalPages?'disabled':''} onclick="mpChangePage(${page+1})">下一页</button>`;
    }
    document.getElementById('mpPagination').innerHTML = pgHtml;
}

function _mpAuditDisabledTip(status) {
    if (status === 'approved') return '该菜谱计划已审核';
    if (status === 'void')     return '该菜谱计划已作废，无需审核';
    if (status === 'deleted')  return '已删除菜谱计划不可操作';
    return '当前状态不可审核';
}

function _mpDeleteDisabledTip(status) {
    if (status === 'approved') return '已审核的菜谱计划不允许删除';
    if (status === 'deleted')  return '该菜谱计划已删除';
    return '当前状态不可删除';
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showRecipePlanDetail(id) {
    const o = (window.mockData.recipePlans || []).find(x => x.id === id);
    if (!o) return;
    const sm          = MP_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };
    const periodLabel = MP_PERIOD_MAP[o.period] || o.period || '—';

    // 菜谱清单行
    const recipeRows = (o.items || []).map(it => {
        const cost     = _calcRecipeEstCost(it.recipeId, it.servings || 0);
        const mealLbl  = MP_MEAL_MAP[it.mealTime] || it.mealTime || '—';
        return `<tr>
            <td>${it.recipeName}</td>
            <td>${mealLbl}</td>
            <td>${it.servings || 0} 份</td>
            <td>${it.isMain ? '<span class="tag tag-primary">主推</span>' : '—'}</td>
            <td>¥${cost.toFixed(2)}</td>
        </tr>`;
    }).join('');

    // 食材明细汇总（跨菜谱合并相同物料）
    const matMap = {};
    (o.items || []).forEach(it => {
        const recipe = (window.mockData.recipes || []).find(r => r.id == it.recipeId);
        if (!recipe) return;
        (recipe.ingredients || []).forEach(ing => {
            const key = ing.materialId + '_' + ing.spec;
            if (!matMap[key]) {
                matMap[key] = {
                    materialName: ing.materialName, spec: ing.spec, unit: ing.unit,
                    perServing: ing.quantity, totalQty: 0
                };
            }
            matMap[key].totalQty += ing.quantity * (it.servings || 0);
        });
    });
    const matRows = Object.values(matMap).map(m => `
        <tr>
            <td>${m.materialName}</td>
            <td>${m.spec || '—'}</td>
            <td>${m.unit}</td>
            <td>${m.perServing} ${m.unit}/份</td>
            <td style="font-weight:500;color:#303133">${m.totalQty.toFixed(2)} ${m.unit}</td>
        </tr>`).join('');

    const totalCost = _calcTotalEstCost(o.items || []);

    const auditSection = o.auditAt ? `
        <div class="detail-section">
            <div class="detail-section-title">审核信息</div>
            <div class="detail-grid">
                <div class="detail-item"><span class="detail-label">审核结果</span>
                    <span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span>
                </div>
                <div class="detail-item"><span class="detail-label">审核人</span>
                    <span class="detail-value">${o.auditorName || '—'}</span>
                </div>
                <div class="detail-item"><span class="detail-label">审核时间</span>
                    <span class="detail-value">${o.auditAt}</span>
                </div>
                ${o.auditRemark ? `<div class="detail-item"><span class="detail-label">审核意见</span><span class="detail-value">${o.auditRemark}</span></div>` : ''}
            </div>
        </div>` : '';

    const html = `
        <div class="modal-header">
            <span class="modal-title">${o.planNo} - 菜谱计划详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">计划单号</span><span class="detail-value">${o.planNo}</span></div>
                    <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span></div>
                    <div class="detail-item"><span class="detail-label">计划名称</span><span class="detail-value">${o.planName}</span></div>
                    <div class="detail-item"><span class="detail-label">适用门店</span><span class="detail-value">${o.orgName}</span></div>
                    <div class="detail-item"><span class="detail-label">计划周期</span><span class="detail-value">${periodLabel}</span></div>
                    <div class="detail-item"><span class="detail-label">计划日期</span><span class="detail-value">${o.planDate}</span></div>
                    <div class="detail-item"><span class="detail-label">制定人</span><span class="detail-value">${o.creatorName}</span></div>
                    <div class="detail-item"><span class="detail-label">预估总成本</span>
                        <span class="detail-value" style="color:#f56c6c;font-weight:500">¥${totalCost.toFixed(2)}</span>
                    </div>
                    ${o.remark ? `<div class="detail-item" style="grid-column:span 2"><span class="detail-label">备注</span><span class="detail-value">${o.remark}</span></div>` : ''}
                </div>
            </div>
            ${auditSection}
            <div class="detail-section">
                <div class="detail-section-title">菜谱清单</div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr>
                            <th>菜谱名称</th><th>执行时段</th><th>计划供应份数</th><th>是否主推</th><th>预估食材成本</th>
                        </tr></thead>
                        <tbody>${recipeRows || '<tr><td colspan="5" style="text-align:center;color:#909399">暂无菜谱</td></tr>'}</tbody>
                    </table>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">食材需求明细（汇总）</div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr>
                            <th>物料名称</th><th>规格</th><th>单位</th><th>单份用量</th><th>总需求量</th>
                        </tr></thead>
                        <tbody>${matRows || '<tr><td colspan="5" style="text-align:center;color:#909399">暂无食材数据</td></tr>'}</tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>`;
    showModal(html, '880px');
}

/* ============================================================
   审核弹窗
   ============================================================ */
function openMpAuditModal(id) {
    const o = (window.mockData.recipePlans || []).find(x => x.id === id);
    if (!o) return;
    if (o.status !== 'pending') {
        showToast(_mpAuditDisabledTip(o.status), 'error');
        return;
    }

    const periodLabel = MP_PERIOD_MAP[o.period] || o.period;
    const totalCost   = _calcTotalEstCost(o.items || []);

    const html = `
        <div class="modal-header">
            <span class="modal-title">审核菜谱计划</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div style="background:#f5f7fa;border-radius:6px;padding:16px;margin-bottom:20px">
                <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:10px 20px;font-size:14px">
                    <div><span style="color:#909399">计划单号：</span><span style="color:#303133;font-weight:500">${o.planNo}</span></div>
                    <div><span style="color:#909399">计划名称：</span><span style="color:#303133">${o.planName}</span></div>
                    <div><span style="color:#909399">适用门店：</span><span style="color:#303133">${o.orgName}</span></div>
                    <div><span style="color:#909399">计划周期：</span><span style="color:#303133">${periodLabel}</span></div>
                    <div><span style="color:#909399">菜谱数量：</span><span style="color:#303133">${(o.items||[]).length} 个</span></div>
                    <div><span style="color:#909399">预估总成本：</span><span style="color:#f56c6c;font-weight:500">¥${totalCost.toFixed(2)}</span></div>
                    ${o.remark ? `<div style="grid-column:span 2"><span style="color:#909399">备注：</span><span style="color:#303133">${o.remark}</span></div>` : ''}
                </div>
            </div>
            <div class="form-item">
                <label class="form-label">审核意见</label>
                <textarea class="form-textarea" id="fMpAuditRemark" rows="3"
                          placeholder="请输入审核意见（可选）" style="width:100%"></textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-danger"  onclick="rejectRecipePlan(${id})" style="margin-left:8px">审核驳回</button>
            <button class="btn btn-primary" onclick="approveRecipePlan(${id})" style="margin-left:8px">审核通过</button>
        </div>`;
    showModal(html, '580px');
}

function approveRecipePlan(id) {
    const o = (window.mockData.recipePlans || []).find(x => x.id === id);
    if (!o) return;
    const remark   = document.getElementById('fMpAuditRemark')?.value || '';
    o.status       = 'approved';
    o.auditAt      = _mpNow();
    o.auditRemark  = remark;
    o.auditorId    = 1;
    o.auditorName  = '管理员';
    o.updatedAt    = _mpNow();
    closeModal();
    showToast('菜谱计划审核通过');
    _filterRecipePlan();
}

function rejectRecipePlan(id) {
    const o = (window.mockData.recipePlans || []).find(x => x.id === id);
    if (!o) return;
    const remark   = document.getElementById('fMpAuditRemark')?.value || '';
    o.status       = 'void';
    o.auditAt      = _mpNow();
    o.auditRemark  = remark;
    o.updatedAt    = _mpNow();
    closeModal();
    showToast('菜谱计划审核驳回，状态置为作废', 'error');
    _filterRecipePlan();
}

/* ============================================================
   删除（软删除）
   ============================================================ */
function deleteRecipePlan(id) {
    const o = (window.mockData.recipePlans || []).find(x => x.id === id);
    if (!o) return;
    if (o.status === 'approved') { showToast('已审核的菜谱计划不允许删除', 'error'); return; }
    if (o.status === 'deleted')  { showToast('该菜谱计划已删除', 'error'); return; }

    const html = `
        <div class="modal-header">
            <span class="modal-title">删除菜谱计划</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div style="text-align:center;padding:20px 0">
                <div style="font-size:48px;margin-bottom:12px">⚠️</div>
                <div style="font-size:16px;font-weight:500;color:#303133;margin-bottom:8px">
                    是否确认删除菜谱计划 <span style="color:#f56c6c">${o.planNo}</span>？
                </div>
                <div style="font-size:13px;color:#909399">删除后标记为已删除，不可恢复</div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-danger"  onclick="_doDeleteRecipePlan(${id})" style="margin-left:8px">确认删除</button>
        </div>`;
    showModal(html, '420px');
}

function _doDeleteRecipePlan(id) {
    const o = (window.mockData.recipePlans || []).find(x => x.id === id);
    if (!o) return;
    o.status    = 'deleted';
    o.updatedAt = _mpNow();
    closeModal();
    showToast('菜谱计划已删除');
    _filterRecipePlan();
}

/* ============================================================
   新增 / 编辑弹窗
   ============================================================ */
function openRecipePlanModal(id) {
    const isEdit = id != null;
    const o = isEdit
        ? JSON.parse(JSON.stringify((window.mockData.recipePlans || []).find(x => x.id === id) || {}))
        : { id: null, planNo: _genMpNo(), planName: '', orgId: '', orgName: '',
            period: 'week', planDate: _mpToday(),
            creatorName: '张三', remark: '', status: 'pending', items: [] };

    if (isEdit && o.status !== 'pending') {
        showToast('仅待审核状态可编辑', 'error');
        return;
    }

    const orgOptions = (window.mockData.orgs || [])
        .filter(g => g.status === 'active')
        .map(g => `<option value="${g.id}" data-name="${g.orgName}" ${o.orgId==g.id?'selected':''}>${g.orgName}</option>`)
        .join('');

    window._mpRowIdx = 5000;

    const html = `
        <div class="modal-header">
            <span class="modal-title">${isEdit ? '编辑菜谱计划' : '新增菜谱计划'}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <div class="form-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 20px">
                <div class="form-item">
                    <label class="form-label required">计划单号</label>
                    <input class="form-input" id="fMpNo" value="${o.planNo}" readonly
                           style="background:#f5f7fa;cursor:not-allowed">
                </div>
                <div class="form-item">
                    <label class="form-label required">计划名称</label>
                    <input class="form-input" id="fMpName" value="${o.planName}" placeholder="请输入计划名称">
                </div>
                <div class="form-item">
                    <label class="form-label required">适用门店</label>
                    <select class="form-select" id="fMpOrgId">
                        <option value="">请选择门店/组织</option>
                        ${orgOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">计划周期</label>
                    <select class="form-select" id="fMpPeriod">
                        <option value="day"   ${o.period==='day'  ?'selected':''}>日</option>
                        <option value="week"  ${o.period==='week' ?'selected':''}>周</option>
                        <option value="month" ${o.period==='month'?'selected':''}>月</option>
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">计划日期</label>
                    <input type="date" class="form-input" id="fMpDate" value="${o.planDate}">
                </div>
                <div class="form-item">
                    <label class="form-label">制定人</label>
                    <input class="form-input" id="fMpCreator" value="${o.creatorName}">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">备注</label>
                    <textarea class="form-textarea" id="fMpRemark" rows="2">${o.remark || ''}</textarea>
                </div>
            </div>

            <!-- 菜谱清单 -->
            <div class="dynamic-table" style="margin-top:20px">
                <div style="font-size:14px;font-weight:600;color:#303133;margin-bottom:10px;
                            border-bottom:1px solid #ebeef5;padding-bottom:8px;
                            display:flex;justify-content:space-between;align-items:center">
                    <span>菜谱清单 <span style="color:#f56c6c;font-size:12px">*</span></span>
                    <span style="font-size:13px;color:#606266;font-weight:normal">
                        预估总成本：¥<span id="mpTotalCost">0.00</span>
                    </span>
                </div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr style="background:#f5f7fa">
                            <th style="min-width:130px">菜谱名称</th>
                            <th style="min-width:110px">执行时段</th>
                            <th style="min-width:90px">供应份数</th>
                            <th style="min-width:80px">是否主推</th>
                            <th style="min-width:100px">预估食材成本</th>
                            <th style="min-width:50px">操作</th>
                        </tr></thead>
                        <tbody id="mpItemBody">
                            ${(o.items || []).map((it, idx) => _buildMpItemRow(idx, it)).join('')}
                        </tbody>
                    </table>
                </div>
                <button class="btn btn-default add-row-btn" onclick="addMpItemRow()" style="margin-top:8px">＋ 添加菜谱</button>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveRecipePlan(${id || 'null'})">提交计划</button>
        </div>`;

    showModal(html, 'min(880px, 96vw)');
    _calcMpTotalCost();
    if (!(o.items || []).length) addMpItemRow();
}

/* ============================================================
   菜谱明细行
   ============================================================ */
function _buildMpItemRow(idx, it) {
    it = it || {};
    const recipes    = (window.mockData.recipes || []);
    const recipeOpts = recipes.map(r =>
        `<option value="${r.id}" data-name="${r.name}" ${it.recipeId==r.id?'selected':''}>${r.name}</option>`
    ).join('');

    const estCost = it.recipeId ? _calcRecipeEstCost(it.recipeId, it.servings || 0) : 0;

    return `<tr id="mpRow_${idx}">
        <td>
            <select class="form-select" style="width:130px" onchange="onMpRecipeChange(this,${idx})">
                <option value="">请选择菜谱</option>
                ${recipeOpts}
            </select>
        </td>
        <td>
            <select class="form-select" style="width:105px" id="mpMeal_${idx}">
                <option value="breakfast" ${(it.mealTime||'lunch')==='breakfast'?'selected':''}>早餐</option>
                <option value="lunch"     ${(it.mealTime||'lunch')==='lunch'    ?'selected':''}>午餐</option>
                <option value="dinner"    ${it.mealTime==='dinner'  ?'selected':''}>晚餐</option>
                <option value="night"     ${it.mealTime==='night'   ?'selected':''}>夜宵</option>
            </select>
        </td>
        <td>
            <input type="number" class="form-input" style="width:82px" id="mpServings_${idx}"
                   value="${it.servings||''}" min="1" step="1" placeholder="份数"
                   oninput="onMpServingsChange(${idx})">
        </td>
        <td>
            <select class="form-select" style="width:75px" id="mpIsMain_${idx}">
                <option value="true"  ${it.isMain===true ?'selected':''}>是</option>
                <option value="false" ${it.isMain===false?'selected':''}>否</option>
            </select>
        </td>
        <td>
            <span id="mpCost_${idx}" style="font-size:14px;color:#f56c6c">¥${estCost.toFixed(2)}</span>
        </td>
        <td>
            <button class="btn-link danger" onclick="removeMpItemRow(${idx})">删除</button>
        </td>
    </tr>`;
}

function addMpItemRow() {
    window._mpRowIdx++;
    const tbody = document.getElementById('mpItemBody');
    if (!tbody) return;
    const tr  = document.createElement('tr');
    tr.id     = `mpRow_${window._mpRowIdx}`;
    const tmp = document.createElement('tbody');
    tmp.innerHTML = _buildMpItemRow(window._mpRowIdx, {});
    const built = tmp.querySelector('tr');
    tr.innerHTML  = built ? built.innerHTML : '';
    tbody.appendChild(tr);
    _calcMpTotalCost();
}

function removeMpItemRow(idx) {
    const row = document.getElementById(`mpRow_${idx}`);
    if (row) row.remove();
    _calcMpTotalCost();
}

function onMpRecipeChange(sel, idx) {
    const recipeId = sel.value;
    const servings = parseFloat(document.getElementById(`mpServings_${idx}`)?.value) || 0;
    const cost     = recipeId ? _calcRecipeEstCost(recipeId, servings) : 0;
    const costEl   = document.getElementById(`mpCost_${idx}`);
    if (costEl) costEl.textContent = '¥' + cost.toFixed(2);
    _calcMpTotalCost();
}

function onMpServingsChange(idx) {
    const sel      = document.querySelector(`#mpRow_${idx} select`);
    const recipeId = sel ? sel.value : '';
    const servings = parseFloat(document.getElementById(`mpServings_${idx}`)?.value) || 0;
    const cost     = recipeId ? _calcRecipeEstCost(recipeId, servings) : 0;
    const costEl   = document.getElementById(`mpCost_${idx}`);
    if (costEl) costEl.textContent = '¥' + cost.toFixed(2);
    _calcMpTotalCost();
}

function _calcMpTotalCost() {
    const tbody = document.getElementById('mpItemBody');
    if (!tbody) return;
    let total = 0;
    tbody.querySelectorAll('tr').forEach(tr => {
        const costEl = tr.querySelector('[id^="mpCost_"]');
        if (costEl) {
            const val = parseFloat(costEl.textContent.replace('¥', '')) || 0;
            total += val;
        }
    });
    const el = document.getElementById('mpTotalCost');
    if (el) el.textContent = total.toFixed(2);
}

/* ============================================================
   保存
   ============================================================ */
function saveRecipePlan(id) {
    const planNo   = document.getElementById('fMpNo')?.value    || '';
    const planName = document.getElementById('fMpName')?.value   || '';
    const orgSel   = document.getElementById('fMpOrgId');
    const orgId    = orgSel?.value || '';
    const planDate = document.getElementById('fMpDate')?.value   || '';
    const period   = document.getElementById('fMpPeriod')?.value || 'week';

    if (!planNo)   { showToast('计划单号不能为空', 'error');  return; }
    if (!planName) { showToast('请输入计划名称', 'error');    return; }
    if (!orgId)    { showToast('请选择适用门店', 'error');    return; }
    if (!planDate) { showToast('请选择计划日期', 'error');    return; }

    const orgName = orgSel.options[orgSel.selectedIndex]?.dataset?.name
                 || orgSel.options[orgSel.selectedIndex]?.text || '';

    // 收集菜谱清单
    const items  = [];
    const tbody  = document.getElementById('mpItemBody');
    if (tbody) {
        tbody.querySelectorAll('tr').forEach(tr => {
            const idx      = tr.id.replace('mpRow_', '');
            const recSel   = tr.querySelector('select');
            const recipeId = recSel ? recSel.value : '';
            if (!recipeId) return;
            const recipeName = recSel.options[recSel.selectedIndex]?.dataset?.name
                            || recSel.options[recSel.selectedIndex]?.text || '';
            const mealTime = document.getElementById(`mpMeal_${idx}`)?.value     || 'lunch';
            const servings = parseInt(document.getElementById(`mpServings_${idx}`)?.value) || 0;
            const isMain   = document.getElementById(`mpIsMain_${idx}`)?.value === 'true';
            if (!recipeName) return;
            items.push({ recipeId: Number(recipeId), recipeName, mealTime, servings, isMain, estCost: 0 });
        });
    }

    if (!items.length) { showToast('请至少添加一个菜谱', 'error'); return; }

    const now = _mpNow();

    if (id) {
        const record = (window.mockData.recipePlans || []).find(o => o.id === id);
        if (record) {
            record.planName    = planName;
            record.orgId       = Number(orgId);
            record.orgName     = orgName;
            record.period      = period;
            record.planDate    = planDate;
            record.creatorName = document.getElementById('fMpCreator')?.value || '张三';
            record.remark      = document.getElementById('fMpRemark')?.value  || '';
            record.items       = items;
            record.updatedAt   = now;
        }
        closeModal();
        showToast('菜谱计划编辑成功');
    } else {
        const plans = window.mockData.recipePlans || (window.mockData.recipePlans = []);
        plans.push({
            id:          plans.length ? Math.max(...plans.map(o => o.id)) + 1 : 1,
            planNo,
            planName,
            orgId:       Number(orgId),
            orgName,
            period,
            planDate,
            creatorId:   1,
            creatorName: document.getElementById('fMpCreator')?.value || '张三',
            auditorId:   null, auditorName: '',
            remark:      document.getElementById('fMpRemark')?.value  || '',
            status:      'pending',
            tenantId:    1,
            createdAt:   now,
            updatedAt:   now,
            items
        });
        closeModal();
        showToast('菜谱计划新增成功');
    }

    _filterRecipePlan();
}

/* ============================================================
   工具函数
   ============================================================ */
function _genMpNo() {
    const d   = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const seq = String(Math.floor(Math.random() * 900 + 100));
    return `MP-${d}-${seq}`;
}

function _mpToday() { return new Date().toISOString().slice(0, 10); }

function _mpNow()   { return new Date().toLocaleString('sv-SE').replace('T', ' '); }
