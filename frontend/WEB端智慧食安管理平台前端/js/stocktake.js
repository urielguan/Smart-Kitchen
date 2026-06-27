/**
 * stocktake.js — 盘点管理
 *
 * ===== 运行验证说明 =====
 *  双击 index.html → 点击左侧「仓储管理 / 盘点管理」导航进入版块
 *
 *  【① 新增盘点表】
 *  - 点击右上角「新增盘点表」→ 填写：盘点仓库（必填）、盘点类型（全盘/抽盘）、盘点日期、盘点人、备注
 *  - 物料清单（必填，至少1行）：物料下拉→规格联动→单位自动带出→系统库存自动读取
 *    → 填写实际盘点数 → 差异数实时自动计算（实际 - 系统）→ 可填差异原因
 *  - 提交后列表新增，状态默认「待审核」，提示"盘点表新增成功"
 *
 *  【② 列表筛选/分页】
 *  - 筛选：盘点单号/盘点仓库/盘点类型/盘点日期范围/状态
 *  - 默认隐藏「已删除」（通过状态筛选可查找）
 *  - 分页10条，含总条数展示
 *
 *  【③ 查看详情】
 *  - 点击「详情」或单号链接 → 只读弹窗，展示基本信息+审核信息（如有）+物料清单（含差异列）
 *
 *  【④ 编辑盘点表】
 *  - 仅「待审核」可编辑，其他状态置灰；精准回显所有字段含物料行；差异数编辑时重新自动计算
 *
 *  【⑤ 审核盘点表】
 *  - 仅「待审核」可审核；审核通过→「已审核」（审核人自动填充）；审核驳回→「已作废」
 *
 *  【⑥ 删除盘点表】
 *  - 「待审核」「已作废」可删除；「已审核」置灰提示"已审核的盘点表不允许删除"
 *  - 二次确认后软删除，默认列表隐藏已删除
 *
 * ===== 核心改动点 =====
 *  1. 新建 js/stocktake.js（本文件）
 *  2. mock-data.js 追加 stocktakeOrders 数组（5条）
 *  3. sidebar.js menuConfig「仓储管理」组：outbound 条目后原有 stocktake 占位已存在，补充 renderPage 分支
 *  4. index.html 追加 <script src="js/stocktake.js">
 *  5. 差异数自动计算：diffQty = actualQty - systemQty，实时联动
 */

/* ============================================================
   常量
   ============================================================ */
const ST_STATUS_MAP = {
    pending:  { label: '待审核', cls: 'tag-warning' },
    approved: { label: '已审核', cls: 'tag-success'  },
    void:     { label: '已作废', cls: 'tag-info'     },
    deleted:  { label: '已删除', cls: 'tag-danger'   }
};

const ST_TYPE_MAP = {
    full:    '全盘',
    partial: '抽盘'
};

/* ============================================================
   状态变量
   ============================================================ */
window.stPage         = 1;
window.stPageSize     = 10;
window.stFilteredList = [];
window._stRowIdx      = 0;

/* ============================================================
   渲染入口
   ============================================================ */
function renderStocktakePage(container) {
    const whNames = [...new Set((window.mockData.warehouses || []).map(w => w.warehouseName))];
    const whOpts  = whNames.map(w => `<option value="${w}">${w}</option>`).join('');

    container.innerHTML = `
        <div class="toolbar">
            <div class="toolbar-row">
                <input class="form-input" id="stSearchNo"  placeholder="盘点单号"   style="width:160px">
                <select class="form-select" id="stSearchWh" style="width:150px">
                    <option value="">全部仓库</option>
                    ${whOpts}
                </select>
                <select class="form-select" id="stSearchType" style="width:110px">
                    <option value="">全部类型</option>
                    <option value="full">全盘</option>
                    <option value="partial">抽盘</option>
                </select>
                <input type="date" class="form-input" id="stSearchDateFrom" style="width:140px" title="盘点日期起">
                <input type="date" class="form-input" id="stSearchDateTo"   style="width:140px" title="盘点日期止">
                <select class="form-select" id="stSearchStatus" style="width:110px">
                    <option value="">全部状态</option>
                    <option value="pending">待审核</option>
                    <option value="approved">已审核</option>
                    <option value="void">已作废</option>
                    <option value="deleted">已删除</option>
                </select>
                <button class="btn btn-primary" onclick="searchStocktake()">搜索</button>
                <button class="btn btn-default" onclick="resetStocktakeSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openStocktakeModal()">＋ 新增盘点表</button>
            </div>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>盘点单号</th>
                        <th>盘点仓库</th>
                        <th>盘点类型</th>
                        <th>盘点日期</th>
                        <th>盘点人</th>
                        <th>物料种数</th>
                        <th>差异物料数</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="stTableBody"></tbody>
            </table>
            <div class="pagination" id="stPagination"></div>
        </div>`;

    _filterStocktake();
}

/* ============================================================
   筛选 & 分页
   ============================================================ */
function _filterStocktake() {
    const no       = (document.getElementById('stSearchNo')?.value      || '').trim().toLowerCase();
    const wh       = (document.getElementById('stSearchWh')?.value      || '');
    const type     = (document.getElementById('stSearchType')?.value    || '');
    const status   = (document.getElementById('stSearchStatus')?.value  || '');
    const dateFrom = (document.getElementById('stSearchDateFrom')?.value || '');
    const dateTo   = (document.getElementById('stSearchDateTo')?.value   || '');

    window.stFilteredList = (window.mockData.stocktakeOrders || []).filter(o => {
        if (!status && o.status === 'deleted')                         return false;
        if (no     && !o.orderNo.toLowerCase().includes(no))          return false;
        if (wh     && o.warehouseName !== wh)                         return false;
        if (type   && o.stocktakeType !== type)                       return false;
        if (status && o.status !== status)                            return false;
        if (dateFrom && o.stocktakeDate < dateFrom)                   return false;
        if (dateTo   && o.stocktakeDate > dateTo)                     return false;
        return true;
    });

    window.stPage = 1;
    _renderStocktakeRows();
}

function searchStocktake()      { _filterStocktake(); }
function resetStocktakeSearch() {
    ['stSearchNo','stSearchWh','stSearchType','stSearchStatus','stSearchDateFrom','stSearchDateTo']
        .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
    _filterStocktake();
}

function stChangePage(p) { window.stPage = p; _renderStocktakeRows(); }

function _renderStocktakeRows() {
    const list     = window.stFilteredList;
    const total    = list.length;
    const pageSize = window.stPageSize;
    const page     = window.stPage;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const tbody = document.getElementById('stTableBody');
    if (!tbody) return;

    if (!pageData.length) {
        tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:40px;color:#909399">暂无盘点表数据</td></tr>`;
        document.getElementById('stPagination').innerHTML = '';
        return;
    }

    tbody.innerHTML = pageData.map(o => {
        const sm        = ST_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };
        const typeLabel = ST_TYPE_MAP[o.stocktakeType] || o.stocktakeType || '—';
        const matCount  = (o.items || []).length;
        const diffCount = (o.items || []).filter(it => it.diffQty !== 0).length;

        // 编辑：仅待审核
        const canEdit = o.status === 'pending';
        const editBtn = canEdit
            ? `<button class="btn-link" onclick="openStocktakeModal(${o.id})">编辑</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('仅待审核状态可编辑','error')">编辑</button>`;

        // 审核：仅待审核
        const canAudit = o.status === 'pending';
        const auditBtn = canAudit
            ? `<button class="btn-link" style="color:#409eff" onclick="openStAuditModal(${o.id})">审核</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('${_stAuditDisabledTip(o.status)}','error')">审核</button>`;

        // 删除：待审核/已作废
        const canDelete = o.status === 'pending' || o.status === 'void';
        const deleteBtn = canDelete
            ? `<button class="btn-link danger" onclick="deleteStocktake(${o.id})">删除</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('${_stDeleteDisabledTip(o.status)}','error')">删除</button>`;

        // 差异物料数着色
        const diffStyle = diffCount > 0 ? 'color:#f56c6c;font-weight:500' : 'color:#909399';

        return `<tr>
            <td><a href="javascript:void(0)" class="btn-link" onclick="showStocktakeDetail(${o.id})">${o.orderNo}</a></td>
            <td>${o.warehouseName}</td>
            <td>${typeLabel}</td>
            <td>${o.stocktakeDate}</td>
            <td>${o.operatorName}</td>
            <td>${matCount} 种</td>
            <td><span style="${diffStyle}">${diffCount} 种</span></td>
            <td><span class="tag ${sm.cls}">${sm.label}</span></td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showStocktakeDetail(${o.id})">详情</button>
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
        pgHtml += ` <button class="btn btn-default" ${page<=1?'disabled':''} onclick="stChangePage(${page-1})">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            pgHtml += `<button class="btn ${i===page?'btn-primary':'btn-default'}" onclick="stChangePage(${i})">${i}</button>`;
        }
        pgHtml += `<button class="btn btn-default" ${page>=totalPages?'disabled':''} onclick="stChangePage(${page+1})">下一页</button>`;
    }
    document.getElementById('stPagination').innerHTML = pgHtml;
}

function _stAuditDisabledTip(status) {
    if (status === 'approved') return '该盘点表已审核';
    if (status === 'void')     return '该盘点表已作废，无需审核';
    if (status === 'deleted')  return '已删除盘点表不可操作';
    return '当前状态不可审核';
}

function _stDeleteDisabledTip(status) {
    if (status === 'approved') return '已审核的盘点表不允许删除';
    if (status === 'deleted')  return '该盘点表已删除';
    return '当前状态不可删除';
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showStocktakeDetail(id) {
    const o = (window.mockData.stocktakeOrders || []).find(x => x.id === id);
    if (!o) return;
    const sm        = ST_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };
    const typeLabel = ST_TYPE_MAP[o.stocktakeType] || o.stocktakeType || '—';

    const itemsHtml = (o.items || []).map(it => {
        const diff      = it.diffQty;
        const diffStyle = diff > 0 ? 'color:#67c23a;font-weight:500'
                        : diff < 0 ? 'color:#f56c6c;font-weight:500'
                        : 'color:#909399';
        const diffSign  = diff > 0 ? `+${diff}` : String(diff);
        return `<tr>
            <td>${it.materialName}</td>
            <td>${it.spec || '—'}</td>
            <td>${it.unit}</td>
            <td>${it.systemQty}</td>
            <td>${it.actualQty}</td>
            <td><span style="${diffStyle}">${diffSign}</span></td>
            <td>${it.diffReason || '—'}</td>
        </tr>`;
    }).join('');

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
            <span class="modal-title">${o.orderNo} - 盘点表详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">盘点单号</span><span class="detail-value">${o.orderNo}</span></div>
                    <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span></div>
                    <div class="detail-item"><span class="detail-label">盘点仓库</span><span class="detail-value">${o.warehouseName}</span></div>
                    <div class="detail-item"><span class="detail-label">盘点类型</span><span class="detail-value">${typeLabel}</span></div>
                    <div class="detail-item"><span class="detail-label">盘点日期</span><span class="detail-value">${o.stocktakeDate}</span></div>
                    <div class="detail-item"><span class="detail-label">盘点人</span><span class="detail-value">${o.operatorName}</span></div>
                    ${o.remark ? `<div class="detail-item" style="grid-column:span 2"><span class="detail-label">备注</span><span class="detail-value">${o.remark}</span></div>` : ''}
                </div>
            </div>
            ${auditSection}
            <div class="detail-section">
                <div class="detail-section-title">盘点物料清单</div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr>
                            <th>物料名称</th><th>规格</th><th>单位</th>
                            <th>系统库存</th><th>实际盘点</th><th>差异数</th><th>差异原因</th>
                        </tr></thead>
                        <tbody>${itemsHtml || '<tr><td colspan="7" style="text-align:center;color:#909399">暂无物料</td></tr>'}</tbody>
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
   审核弹窗
   ============================================================ */
function openStAuditModal(id) {
    const o = (window.mockData.stocktakeOrders || []).find(x => x.id === id);
    if (!o) return;
    if (o.status !== 'pending') {
        showToast(_stAuditDisabledTip(o.status), 'error');
        return;
    }

    const typeLabel = ST_TYPE_MAP[o.stocktakeType] || o.stocktakeType;
    const matCount  = (o.items || []).length;
    const diffCount = (o.items || []).filter(it => it.diffQty !== 0).length;

    const html = `
        <div class="modal-header">
            <span class="modal-title">审核盘点表</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div style="background:#f5f7fa;border-radius:6px;padding:16px;margin-bottom:20px">
                <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:10px 20px;font-size:14px">
                    <div><span style="color:#909399">盘点单号：</span><span style="color:#303133;font-weight:500">${o.orderNo}</span></div>
                    <div><span style="color:#909399">盘点类型：</span><span style="color:#303133">${typeLabel}</span></div>
                    <div><span style="color:#909399">盘点仓库：</span><span style="color:#303133">${o.warehouseName}</span></div>
                    <div><span style="color:#909399">盘点日期：</span><span style="color:#303133">${o.stocktakeDate}</span></div>
                    <div><span style="color:#909399">物料种数：</span><span style="color:#303133">${matCount} 种</span></div>
                    <div><span style="color:#909399">差异物料：</span>
                        <span style="${diffCount>0?'color:#f56c6c;font-weight:500':'color:#67c23a'}">${diffCount} 种</span>
                    </div>
                    ${o.remark ? `<div style="grid-column:span 2"><span style="color:#909399">备注：</span><span style="color:#303133">${o.remark}</span></div>` : ''}
                </div>
            </div>
            <div class="form-item">
                <label class="form-label">审核意见</label>
                <textarea class="form-textarea" id="fStAuditRemark" rows="3"
                          placeholder="请输入审核意见（可选）" style="width:100%"></textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-danger"  onclick="rejectStocktake(${id})" style="margin-left:8px">审核驳回</button>
            <button class="btn btn-primary" onclick="approveStocktake(${id})" style="margin-left:8px">审核通过</button>
        </div>`;
    showModal(html, '580px');
}

function approveStocktake(id) {
    const o = (window.mockData.stocktakeOrders || []).find(x => x.id === id);
    if (!o) return;
    const remark   = document.getElementById('fStAuditRemark')?.value || '';
    o.status       = 'approved';
    o.auditAt      = _stNow();
    o.auditRemark  = remark;
    o.auditorId    = 1;
    o.auditorName  = '管理员';
    o.updatedAt    = _stNow();
    closeModal();
    showToast('盘点表审核通过');
    _filterStocktake();
}

function rejectStocktake(id) {
    const o = (window.mockData.stocktakeOrders || []).find(x => x.id === id);
    if (!o) return;
    const remark   = document.getElementById('fStAuditRemark')?.value || '';
    o.status       = 'void';
    o.auditAt      = _stNow();
    o.auditRemark  = remark;
    o.updatedAt    = _stNow();
    closeModal();
    showToast('盘点表审核驳回，状态置为作废', 'error');
    _filterStocktake();
}

/* ============================================================
   删除（软删除）
   ============================================================ */
function deleteStocktake(id) {
    const o = (window.mockData.stocktakeOrders || []).find(x => x.id === id);
    if (!o) return;
    if (o.status === 'approved') {
        showToast('已审核的盘点表不允许删除', 'error');
        return;
    }
    if (o.status === 'deleted') {
        showToast('该盘点表已删除', 'error');
        return;
    }

    const html = `
        <div class="modal-header">
            <span class="modal-title">删除盘点表</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div style="text-align:center;padding:20px 0">
                <div style="font-size:48px;margin-bottom:12px">⚠️</div>
                <div style="font-size:16px;font-weight:500;color:#303133;margin-bottom:8px">
                    是否确认删除盘点表 <span style="color:#f56c6c">${o.orderNo}</span>？
                </div>
                <div style="font-size:13px;color:#909399">
                    删除后标记为已删除，不可恢复
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-danger"  onclick="_doDeleteStocktake(${id})" style="margin-left:8px">确认删除</button>
        </div>`;
    showModal(html, '420px');
}

function _doDeleteStocktake(id) {
    const o = (window.mockData.stocktakeOrders || []).find(x => x.id === id);
    if (!o) return;
    o.status    = 'deleted';
    o.updatedAt = _stNow();
    closeModal();
    showToast('盘点表已删除');
    _filterStocktake();
}

/* ============================================================
   新增 / 编辑弹窗
   ============================================================ */
function openStocktakeModal(id) {
    const isEdit = id != null;
    const o = isEdit
        ? JSON.parse(JSON.stringify((window.mockData.stocktakeOrders || []).find(x => x.id === id) || {}))
        : { id: null, orderNo: _genStNo(), warehouseId: '', warehouseName: '',
            stocktakeType: 'full', stocktakeDate: _stToday(),
            operatorName: '张三', remark: '', status: 'pending', items: [] };

    if (isEdit && o.status !== 'pending') {
        showToast('仅待审核状态可编辑', 'error');
        return;
    }

    const whOptions = (window.mockData.warehouses || [])
        .map(w => `<option value="${w.id}" data-name="${w.warehouseName}" ${o.warehouseId==w.id?'selected':''}>${w.warehouseName}</option>`)
        .join('');

    window._stRowIdx = 4000;

    const html = `
        <div class="modal-header">
            <span class="modal-title">${isEdit ? '编辑盘点表' : '新增盘点表'}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <div class="form-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 20px">
                <div class="form-item">
                    <label class="form-label required">盘点单号</label>
                    <input class="form-input" id="fStNo" value="${o.orderNo}" readonly
                           style="background:#f5f7fa;cursor:not-allowed">
                </div>
                <div class="form-item">
                    <label class="form-label required">盘点仓库</label>
                    <select class="form-select" id="fStWarehouseId">
                        <option value="">请选择仓库</option>
                        ${whOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">盘点类型</label>
                    <select class="form-select" id="fStType">
                        <option value="full"    ${o.stocktakeType==='full'   ?'selected':''}>全盘</option>
                        <option value="partial" ${o.stocktakeType==='partial'?'selected':''}>抽盘</option>
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">盘点日期</label>
                    <input type="date" class="form-input" id="fStDate" value="${o.stocktakeDate}">
                </div>
                <div class="form-item">
                    <label class="form-label">盘点人</label>
                    <input class="form-input" id="fStOperator" value="${o.operatorName}">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">备注</label>
                    <textarea class="form-textarea" id="fStRemark" rows="2">${o.remark || ''}</textarea>
                </div>
            </div>

            <!-- 盘点物料清单 -->
            <div class="dynamic-table" style="margin-top:20px">
                <div style="font-size:14px;font-weight:600;color:#303133;margin-bottom:10px;
                            border-bottom:1px solid #ebeef5;padding-bottom:8px">
                    盘点物料清单 <span style="color:#f56c6c;font-size:12px">*</span>
                    <span style="font-size:12px;color:#909399;font-weight:normal;margin-left:8px">
                        差异数 = 实际盘点 - 系统库存，自动计算
                    </span>
                </div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr style="background:#f5f7fa">
                            <th style="min-width:120px">物料名称</th>
                            <th style="min-width:120px">规格</th>
                            <th style="min-width:60px">单位</th>
                            <th style="min-width:80px">系统库存</th>
                            <th style="min-width:90px">实际盘点数</th>
                            <th style="min-width:75px">差异数</th>
                            <th style="min-width:140px">差异原因</th>
                            <th style="min-width:50px">操作</th>
                        </tr></thead>
                        <tbody id="stItemBody">
                            ${(o.items || []).map((it, idx) => _buildStItemRow(idx, it)).join('')}
                        </tbody>
                    </table>
                </div>
                <button class="btn btn-default add-row-btn" onclick="addStItemRow()" style="margin-top:8px">＋ 添加物料</button>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveStocktake(${id || 'null'})">提交盘点</button>
        </div>`;

    showModal(html, 'min(940px, 96vw)');
    if (!(o.items || []).length) addStItemRow();
}

/* ============================================================
   物料明细行
   ============================================================ */
function _buildStItemRow(idx, it) {
    it = it || {};
    const materials   = (window.mockData.materials || []).map(m => ({
        id: m.id, name: m.materialName || m.name, unit: m.unit, spec: m.spec || ''
    }));
    const matOptions  = materials.map(m =>
        `<option value="${m.id}" data-unit="${m.unit}" data-name="${m.name}" data-spec="${m.spec}"
                 ${it.materialId==m.id?'selected':''}>${m.name}</option>`
    ).join('');

    const selMat      = materials.find(m => m.id == it.materialId);
    const specList    = selMat ? selMat.spec.split(';').map(s=>s.trim()).filter(Boolean) : [];
    const specOptions = specList.length
        ? specList.map(s => `<option value="${s}" ${it.spec===s?'selected':''}>${s}</option>`).join('')
        : (it.spec ? `<option value="${it.spec}" selected>${it.spec}</option>` : '');

    const systemQty = it.systemQty != null ? it.systemQty : '';
    const actualQty = it.actualQty != null ? it.actualQty : '';
    const diffQty   = it.diffQty   != null ? it.diffQty   : 0;
    const diffStyle = diffQty > 0 ? 'color:#67c23a;font-weight:500'
                    : diffQty < 0 ? 'color:#f56c6c;font-weight:500'
                    : 'color:#909399';
    const diffSign  = diffQty > 0 ? `+${diffQty}` : String(diffQty);

    return `<tr id="stRow_${idx}">
        <td>
            <select class="form-select" style="width:120px" onchange="onStMatChange(this,${idx})">
                <option value="">请选择</option>
                ${matOptions}
            </select>
        </td>
        <td>
            <select class="form-select" style="width:120px" id="stSpec_${idx}">
                <option value="">请选择规格</option>
                ${specOptions}
            </select>
        </td>
        <td>
            <input class="form-input" style="width:55px;background:#f5f7fa;cursor:not-allowed"
                   id="stUnit_${idx}" value="${it.unit||''}" readonly>
        </td>
        <td>
            <input type="number" class="form-input"
                   style="width:75px;background:#f5f7fa;cursor:not-allowed"
                   id="stSysQty_${idx}" value="${systemQty}" readonly>
        </td>
        <td>
            <input type="number" class="form-input" style="width:85px" id="stActQty_${idx}"
                   value="${actualQty}" min="0" step="0.01" placeholder="实际数"
                   oninput="_calcStDiff(${idx})">
        </td>
        <td>
            <span id="stDiff_${idx}" style="${diffStyle}">${actualQty !== '' ? diffSign : '—'}</span>
        </td>
        <td>
            <input class="form-input" style="width:135px" id="stDiffReason_${idx}"
                   value="${it.diffReason||''}" placeholder="差异原因（可选）">
        </td>
        <td>
            <button class="btn-link danger" onclick="removeStItemRow(${idx})">删除</button>
        </td>
    </tr>`;
}

function addStItemRow() {
    window._stRowIdx++;
    const tbody = document.getElementById('stItemBody');
    if (!tbody) return;
    const tr  = document.createElement('tr');
    tr.id     = `stRow_${window._stRowIdx}`;
    const tmp = document.createElement('tbody');
    tmp.innerHTML = _buildStItemRow(window._stRowIdx, {});
    const built = tmp.querySelector('tr');
    tr.innerHTML  = built ? built.innerHTML : '';
    tbody.appendChild(tr);
}

function removeStItemRow(idx) {
    const row = document.getElementById(`stRow_${idx}`);
    if (row) row.remove();
}

function onStMatChange(sel, idx) {
    const opt      = sel.options[sel.selectedIndex];
    const unit     = opt.dataset.unit || '';
    const spec     = opt.dataset.spec || '';
    const matName  = opt.dataset.name || '';
    const matId    = opt.value;

    // 单位回填
    const unitEl = document.getElementById(`stUnit_${idx}`);
    if (unitEl) unitEl.value = unit;

    // 规格联动
    const specEl = document.getElementById(`stSpec_${idx}`);
    if (specEl) {
        const selMat   = (window.mockData.materials || []).find(m => (m.materialName||m.name) === matName);
        const specList = selMat ? (selMat.spec||'').split(';').map(s=>s.trim()).filter(Boolean) : [];
        const opts     = specList.length
            ? specList.map(s => `<option value="${s}">${s}</option>`).join('')
            : (spec ? `<option value="${spec}">${spec}</option>` : '');
        specEl.innerHTML = `<option value="">请选择规格</option>${opts}`;
    }

    // 系统库存读取：从 inventorySummary 中按 materialId 查找（优先仓库匹配）
    const sysQtyEl = document.getElementById(`stSysQty_${idx}`);
    if (sysQtyEl && matId) {
        const whSel = document.getElementById('fStWarehouseId');
        const whId  = whSel ? Number(whSel.value) : 0;
        const inv   = (window.mockData.inventorySummary || []).find(
            s => s.materialId == matId && (whId ? s.warehouseId === whId : true)
        ) || (window.mockData.inventorySummary || []).find(s => s.materialId == matId);
        sysQtyEl.value = inv ? inv.currentQty : 0;
        _calcStDiff(idx);
    }
}

/* 差异数自动计算：差异 = 实际 - 系统 */
function _calcStDiff(idx) {
    const sysQty = parseFloat(document.getElementById(`stSysQty_${idx}`)?.value) || 0;
    const actQty = document.getElementById(`stActQty_${idx}`)?.value;
    const diffEl = document.getElementById(`stDiff_${idx}`);
    if (!diffEl) return;

    if (actQty === '' || actQty === null || actQty === undefined) {
        diffEl.textContent = '—';
        diffEl.style.color = '#909399';
        diffEl.style.fontWeight = 'normal';
        return;
    }

    const actual = parseFloat(actQty) || 0;
    const diff   = actual - sysQty;
    const sign   = diff > 0 ? `+${diff}` : String(diff);
    diffEl.textContent = sign;
    if (diff > 0)      { diffEl.style.color = '#67c23a'; diffEl.style.fontWeight = '500'; }
    else if (diff < 0) { diffEl.style.color = '#f56c6c'; diffEl.style.fontWeight = '500'; }
    else               { diffEl.style.color = '#909399'; diffEl.style.fontWeight = 'normal'; }
}

/* ============================================================
   保存
   ============================================================ */
function saveStocktake(id) {
    const orderNo     = document.getElementById('fStNo')?.value || '';
    const whSel       = document.getElementById('fStWarehouseId');
    const warehouseId = whSel?.value || '';
    const stDate      = document.getElementById('fStDate')?.value || '';
    const stType      = document.getElementById('fStType')?.value || 'full';

    if (!orderNo)     { showToast('盘点单号不能为空', 'error'); return; }
    if (!warehouseId) { showToast('请选择盘点仓库', 'error');   return; }
    if (!stDate)      { showToast('请选择盘点日期', 'error');   return; }

    const warehouseName = whSel.options[whSel.selectedIndex]?.dataset?.name
                       || whSel.options[whSel.selectedIndex]?.text || '';

    // 收集物料清单
    const items  = [];
    const tbody  = document.getElementById('stItemBody');
    if (tbody) {
        tbody.querySelectorAll('tr').forEach(tr => {
            const idx    = tr.id.replace('stRow_', '');
            const matSel = tr.querySelector('select');
            const matId  = matSel ? matSel.value : '';
            if (!matId) return;
            const matName    = matSel.options[matSel.selectedIndex]?.dataset?.name
                            || matSel.options[matSel.selectedIndex]?.text || '';
            const specEl     = document.getElementById(`stSpec_${idx}`);
            const spec       = specEl?.value || '';
            const unit       = document.getElementById(`stUnit_${idx}`)?.value  || '';
            const sysQtyRaw  = document.getElementById(`stSysQty_${idx}`)?.value;
            const actQtyRaw  = document.getElementById(`stActQty_${idx}`)?.value;
            const systemQty  = parseFloat(sysQtyRaw) || 0;
            const actualQty  = actQtyRaw !== '' ? (parseFloat(actQtyRaw) || 0) : null;
            const diffReason = document.getElementById(`stDiffReason_${idx}`)?.value || '';
            if (!matName) return;
            const diffQty = actualQty != null ? actualQty - systemQty : 0;
            items.push({ materialId: Number(matId), materialName: matName, spec, unit,
                         systemQty, actualQty: actualQty != null ? actualQty : 0, diffQty, diffReason });
        });
    }

    if (!items.length) { showToast('请至少添加一条盘点物料', 'error'); return; }

    const now = _stNow();

    if (id) {
        const record = (window.mockData.stocktakeOrders || []).find(o => o.id === id);
        if (record) {
            record.warehouseId   = Number(warehouseId);
            record.warehouseName = warehouseName;
            record.stocktakeType = stType;
            record.stocktakeDate = stDate;
            record.operatorName  = document.getElementById('fStOperator')?.value || '张三';
            record.remark        = document.getElementById('fStRemark')?.value   || '';
            record.items         = items;
            record.updatedAt     = now;
        }
        closeModal();
        showToast('盘点表编辑成功');
    } else {
        const orders = window.mockData.stocktakeOrders || (window.mockData.stocktakeOrders = []);
        orders.push({
            id:             orders.length ? Math.max(...orders.map(o => o.id)) + 1 : 1,
            orderNo,
            warehouseId:    Number(warehouseId),
            warehouseName,
            stocktakeType:  stType,
            stocktakeDate:  stDate,
            operatorId:     1,
            operatorName:   document.getElementById('fStOperator')?.value || '张三',
            auditorId:      null, auditorName: '',
            remark:         document.getElementById('fStRemark')?.value || '',
            status:         'pending',
            tenantId:       1,
            createdAt:      now,
            updatedAt:      now,
            items
        });
        closeModal();
        showToast('盘点表新增成功');
    }

    _filterStocktake();
}

/* ============================================================
   工具函数
   ============================================================ */
function _genStNo() {
    const d   = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const seq = String(Math.floor(Math.random() * 900 + 100));
    return `ST-${d}-${seq}`;
}

function _stToday() { return new Date().toISOString().slice(0, 10); }

function _stNow()   { return new Date().toLocaleString('sv-SE').replace('T', ' '); }
