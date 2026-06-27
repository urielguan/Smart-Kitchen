/**
 * inventory.js — 库存汇总管理
 *
 * ===== 运行验证说明 =====
 *  1. 双击 index.html → 点击左侧「仓储管理 / 库存汇总」导航进入版块
 *
 *  【优化点1：库存分布明细】
 *  - 列表操作列新增「库存分布」按钮（与「详情」并列）
 *  - 点击任意行「库存分布」→ 弹窗标题"XX - 库存分布明细"，展示该物料在各仓库/仓位的分布数据
 *  - 弹窗内可通过「筛选仓库」下拉对分布记录进行过滤，无数据时显示空状态提示
 *
 *  【优化点2：出入库记录明细】
 *  - 列表操作列新增「出入库记录」按钮（与「库存分布」「详情」并列）
 *  - 点击任意行「出入库记录」→ 弹窗标题"XX - 出入库记录明细"
 *  - 弹窗支持：操作类型筛选（入库/出库/调拨）、时间范围（近7天/近30天/自定义）、关键词搜索
 *  - 操作数量颜色区分：入库绿色+号、出库红色-号、调拨蓝色箭头
 *  - 支持分页（每页10条），无数据时显示空状态
 *
 * ===== 核心改动点 =====
 *  1. _renderInventoryRows：操作列新增「库存分布」「出入库记录」两个按钮
 *  2. 新增 showInvDistribution(id)：库存分布弹窗（含仓库筛选 + 分布明细表）
 *  3. 新增 _genMockDistribution(inv)：基于仓库数据生成多仓位分布 Mock 数据
 *  4. 新增 filterInvDistribution()：弹窗内仓库筛选实时刷新
 *  5. 新增 showInvLogs(id)：出入库记录弹窗（含类型/时间/关键词筛选 + 分页）
 *  6. 新增 _genMockInvLogs(inv)：生成含记录编号/关联单据的完整出入库 Mock 数据
 *  7. 新增 filterInvLogs() / invLogsChangePage(p)：弹窗内筛选与分页
 *  8. 原有所有函数（renderInventoryPage / _filterInventory / showInventoryDetail 等）完全保持不变
 */

/* ============================================================
   常量（不变）
   ============================================================ */
const INV_STATUS_MAP = {
    normal:   { label: '正常',    cls: 'tag-success' },
    warning:  { label: '临期',    cls: 'tag-warning'  },
    shortage: { label: '库存不足', cls: 'tag-danger'  },
    out:      { label: '已缺货',  cls: 'tag-danger'   }
};

/* ============================================================
   状态变量（不变）
   ============================================================ */
window.invPage         = 1;
window.invPageSize     = 10;
window.invFilteredList = [];

/* 出入库记录弹窗专用状态 */
window._invLogsAll      = [];   // 当前物料全量日志
window._invLogsFiltered = [];   // 筛选后日志
window._invLogsPage     = 1;
window._invLogsPageSize = 10;

/* 库存分布弹窗专用状态 */
window._invDistAll      = [];   // 当前物料全量分布
window._invDistFiltered = [];   // 筛选后分布

/* ============================================================
   渲染入口（不变）
   ============================================================ */
function renderInventoryPage(container) {
    const list = window.mockData.inventorySummary || [];

    const totalSku      = list.length;
    const totalValue    = list.reduce((s, i) => s + (i.totalValue || 0), 0);
    const warningCount  = list.filter(i => i.status === 'warning').length;
    const shortageCount = list.filter(i => ['shortage','out'].includes(i.status)).length;

    const warehouses = [...new Set(list.map(i => i.warehouseName))];
    const whOptions  = warehouses.map(w => `<option value="${w}">${w}</option>`).join('');

    container.innerHTML = `
        <div class="stats-cards" style="grid-template-columns:repeat(4,1fr)">
            <div class="stat-card">
                <div class="stat-card-title">物料 SKU 总数</div>
                <div class="stat-card-value" style="color:#409eff">${totalSku}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">库存总价值（元）</div>
                <div class="stat-card-value" style="color:#67c23a">¥${totalValue.toFixed(2)}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">临期预警</div>
                <div class="stat-card-value" style="color:#e6a23c">${warningCount}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">缺货 / 库存不足</div>
                <div class="stat-card-value" style="color:#f56c6c">${shortageCount}</div>
            </div>
        </div>

        <div class="toolbar">
            <div class="toolbar-row">
                <input class="form-input" id="invSearchMat" placeholder="物料名称" style="width:160px">
                <select class="form-select" id="invSearchWh" style="width:160px">
                    <option value="">全部仓库</option>
                    ${whOptions}
                </select>
                <select class="form-select" id="invSearchStatus" style="width:130px">
                    <option value="">全部状态</option>
                    <option value="normal">正常</option>
                    <option value="warning">临期</option>
                    <option value="shortage">库存不足</option>
                    <option value="out">已缺货</option>
                </select>
                <button class="btn btn-primary" onclick="searchInventory()">搜索</button>
                <button class="btn btn-default" onclick="resetInventorySearch()">重置</button>
            </div>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>物料名称</th>
                        <th>分类</th>
                        <th>规格</th>
                        <th>单位</th>
                        <th>所在仓库</th>
                        <th>库位</th>
                        <th>当前库存</th>
                        <th>可用库存</th>
                        <th>安全库存</th>
                        <th>库存价值</th>
                        <th>到期日期</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="invTableBody"></tbody>
            </table>
            <div class="pagination" id="invPagination"></div>
        </div>`;

    _filterInventory();
}

/* ============================================================
   筛选 & 分页（不变）
   ============================================================ */
function _filterInventory() {
    const mat    = (document.getElementById('invSearchMat')?.value    || '').trim().toLowerCase();
    const wh     = (document.getElementById('invSearchWh')?.value     || '');
    const status = (document.getElementById('invSearchStatus')?.value || '');

    window.invFilteredList = (window.mockData.inventorySummary || []).filter(i => {
        if (mat    && !i.materialName.toLowerCase().includes(mat)) return false;
        if (wh     && i.warehouseName !== wh)                       return false;
        if (status && i.status !== status)                           return false;
        return true;
    });

    window.invPage = 1;
    _renderInventoryRows();
}

function searchInventory()  { _filterInventory(); }
function resetInventorySearch() {
    ['invSearchMat','invSearchWh','invSearchStatus']
        .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
    _filterInventory();
}

function invChangePage(p) { window.invPage = p; _renderInventoryRows(); }

/* ============================================================
   渲染列表行（新增「库存分布」「出入库记录」按钮）
   ============================================================ */
function _renderInventoryRows() {
    const list     = window.invFilteredList;
    const total    = list.length;
    const pageSize = window.invPageSize;
    const page     = window.invPage;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const tbody = document.getElementById('invTableBody');
    if (!tbody) return;

    if (!pageData.length) {
        tbody.innerHTML = `<tr><td colspan="13" style="text-align:center;padding:40px;color:#909399">暂无数据</td></tr>`;
        document.getElementById('invPagination').innerHTML = '';
        return;
    }

    tbody.innerHTML = pageData.map(i => {
        const sm = INV_STATUS_MAP[i.status] || { label: i.status, cls: 'tag-info' };

        let rowCls = '';
        if (i.status === 'warning')                    rowCls = 'warning-row';
        if (['shortage','out'].includes(i.status))     rowCls = 'danger-row';

        const expiryTxt = i.expiryDate || '—';

        return `<tr class="${rowCls}">
            <td><a href="javascript:void(0)" class="btn-link" onclick="showInventoryDetail(${i.id})">${i.materialName}</a></td>
            <td>${i.categoryName}</td>
            <td>${i.spec}</td>
            <td>${i.unit}</td>
            <td>${i.warehouseName}</td>
            <td>${i.locationCode}</td>
            <td>${i.currentQty}</td>
            <td>${i.availableQty}</td>
            <td>${i.safeQty}</td>
            <td>¥${(i.totalValue || 0).toFixed(2)}</td>
            <td>${expiryTxt}</td>
            <td><span class="tag ${sm.cls}">${sm.label}</span></td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showInventoryDetail(${i.id})">详情</button>
                    <button class="btn-link" style="color:#909399" onclick="showInvDistribution(${i.id})">库存分布</button>
                    <button class="btn-link" style="color:#909399" onclick="showInvLogs(${i.id})">出入库记录</button>
                </div>
            </td>
        </tr>`;
    }).join('');

    const totalPages = Math.ceil(total / pageSize);
    let pgHtml = `<span style="color:#606266;font-size:14px">共 ${total} 条</span>`;
    if (totalPages > 1) {
        pgHtml += ` <button class="btn btn-default" ${page<=1?'disabled':''} onclick="invChangePage(${page-1})">上一页</button>`;
        for (let p = 1; p <= totalPages; p++) {
            pgHtml += `<button class="btn ${p===page?'btn-primary':'btn-default'}" onclick="invChangePage(${p})">${p}</button>`;
        }
        pgHtml += `<button class="btn btn-default" ${page>=totalPages?'disabled':''} onclick="invChangePage(${page+1})">下一页</button>`;
    }
    document.getElementById('invPagination').innerHTML = pgHtml;
}

/* ============================================================
   详情弹窗（不变）
   ============================================================ */
function showInventoryDetail(id) {
    const inv = (window.mockData.inventorySummary || []).find(x => x.id === id);
    if (!inv) return;
    const sm = INV_STATUS_MAP[inv.status] || { label: inv.status, cls: 'tag-info' };

    const mockLogs = _genMockLogs(inv);
    const logsHtml = mockLogs.map(l => `
        <tr>
            <td>${l.bizType}</td>
            <td style="color:${l.qty > 0 ? '#67c23a' : '#f56c6c'}">${l.qty > 0 ? '+' : ''}${l.qty} ${inv.unit}</td>
            <td>${l.batchNo}</td>
            <td>${l.operator}</td>
            <td>${l.time}</td>
            <td>${l.remark}</td>
        </tr>`).join('');

    const html = `
        <div class="modal-header">
            <span class="modal-title">库存详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">物料名称</span><span class="detail-value">${inv.materialName}</span></div>
                    <div class="detail-item"><span class="detail-label">分类</span><span class="detail-value">${inv.categoryName}</span></div>
                    <div class="detail-item"><span class="detail-label">规格</span><span class="detail-value">${inv.spec}</span></div>
                    <div class="detail-item"><span class="detail-label">单位</span><span class="detail-value">${inv.unit}</span></div>
                    <div class="detail-item"><span class="detail-label">所在仓库</span><span class="detail-value">${inv.warehouseName}</span></div>
                    <div class="detail-item"><span class="detail-label">库位编号</span><span class="detail-value">${inv.locationCode}</span></div>
                    <div class="detail-item"><span class="detail-label">批次号</span><span class="detail-value">${inv.batchNo || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">生产日期</span><span class="detail-value">${inv.productionDate || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">到期日期</span><span class="detail-value">${inv.expiryDate || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">库存数量</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">当前库存</span><span class="detail-value" style="font-size:18px;font-weight:bold;color:#303133">${inv.currentQty} ${inv.unit}</span></div>
                    <div class="detail-item"><span class="detail-label">可用库存</span><span class="detail-value" style="color:#67c23a;font-weight:bold">${inv.availableQty} ${inv.unit}</span></div>
                    <div class="detail-item"><span class="detail-label">锁定数量</span><span class="detail-value">${inv.lockQty} ${inv.unit}</span></div>
                    <div class="detail-item"><span class="detail-label">安全库存</span><span class="detail-value">${inv.safeQty} ${inv.unit}</span></div>
                    <div class="detail-item"><span class="detail-label">平均成本价</span><span class="detail-value">¥${(inv.avgCostPrice || 0).toFixed(2)}</span></div>
                    <div class="detail-item"><span class="detail-label">库存价值</span><span class="detail-value" style="color:#f56c6c;font-weight:bold">¥${(inv.totalValue || 0).toFixed(2)}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">近期出入库记录</div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr>
                            <th>业务类型</th><th>变动数量</th><th>批次号</th>
                            <th>操作人</th><th>操作时间</th><th>备注</th>
                        </tr></thead>
                        <tbody>${logsHtml}</tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>`;
    showModal(html, '780px');
}

/* ============================================================
   _genMockLogs（不变）
   ============================================================ */
function _genMockLogs(inv) {
    const operators = ['张三', '李四', '王五'];
    const biz = [
        { bizType: '采购入库', qty: +Math.floor(Math.random()*50+10), remark: '采购订单入库' },
        { bizType: '领料出库', qty: -Math.floor(Math.random()*20+5),  remark: '后厨领料' },
        { bizType: '领料出库', qty: -Math.floor(Math.random()*15+3),  remark: '后厨领料' },
        { bizType: '采购入库', qty: +Math.floor(Math.random()*30+5),  remark: '紧急采购补货' },
        { bizType: '盘点调整', qty: -(Math.floor(Math.random()*3)),    remark: '盘点损耗调整' }
    ];
    const now = new Date();
    return biz.map((b, idx) => {
        const d = new Date(now.getTime() - idx * 24 * 3600 * 1000);
        return {
            ...b,
            batchNo:  inv.batchNo || `BATCH-${d.toISOString().slice(0,10).replace(/-/g,'')}`,
            operator: operators[idx % operators.length],
            time:     d.toLocaleString('sv-SE').replace('T', ' ').slice(0, 16)
        };
    });
}

/* ============================================================
   【优化点1】库存分布弹窗
   ============================================================ */
function showInvDistribution(id) {
    const inv = (window.mockData.inventorySummary || []).find(x => x.id === id);
    if (!inv) return;

    window._invDistAll = _genMockDistribution(inv);

    /* 仓库筛选选项 */
    const whs = [...new Set(window._invDistAll.map(d => d.warehouseName))];
    const whOpts = whs.map(w => `<option value="${w}">${w}</option>`).join('');

    const html = `
        <div class="modal-header">
            <span class="modal-title">${inv.materialName} - 库存分布明细</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <!-- 筛选栏 -->
            <div style="display:flex;gap:10px;align-items:center;margin-bottom:16px;flex-wrap:wrap">
                <select class="form-select" id="distWhFilter" style="width:170px"
                        onchange="filterInvDistribution()">
                    <option value="">全部仓库</option>
                    ${whOpts}
                </select>
                <span style="font-size:13px;color:#909399">共 <span id="distTotal">${window._invDistAll.length}</span> 条分布记录</span>
            </div>

            <!-- 分布明细表 -->
            <div id="distTableWrap">
                ${_renderDistTable(window._invDistAll, inv.unit)}
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>`;

    showModal(html, '740px');
}

function filterInvDistribution() {
    const wh = document.getElementById('distWhFilter')?.value || '';
    window._invDistFiltered = wh
        ? window._invDistAll.filter(d => d.warehouseName === wh)
        : window._invDistAll.slice();

    const inv = { unit: '' };   // 仅用于单位，从第一条取
    const unit = (window._invDistAll[0] || {}).unit || '';

    document.getElementById('distTotal').textContent = window._invDistFiltered.length;
    document.getElementById('distTableWrap').innerHTML =
        _renderDistTable(window._invDistFiltered, unit);
}

function _renderDistTable(rows, unit) {
    if (!rows.length) {
        return `<div style="text-align:center;padding:40px;color:#909399">该物料暂无库存分布数据</div>`;
    }
    const rowsHtml = rows.map(d => {
        const stMap = { normal: { label:'正常', cls:'tag-success' }, warning: { label:'临期', cls:'tag-warning' } };
        const st = stMap[d.status] || { label: d.status, cls: 'tag-info' };
        return `<tr>
            <td>${d.warehouseName}</td>
            <td>${d.locationCode}</td>
            <td style="font-weight:600;color:#303133">${d.currentQty}</td>
            <td>${d.unit}</td>
            <td>${d.lockQty}</td>
            <td>${d.batchNo || '—'}</td>
            <td>${d.expiryDate || '—'}</td>
            <td><span class="tag ${st.cls}">${st.label}</span></td>
            <td>${d.lastCheckTime}</td>
        </tr>`;
    }).join('');

    return `<table style="width:100%">
        <thead>
            <tr>
                <th>仓库名称</th>
                <th>仓位编号</th>
                <th>库存数量</th>
                <th>单位</th>
                <th>锁定数量</th>
                <th>批次号</th>
                <th>到期日期</th>
                <th>库存状态</th>
                <th>最后盘点时间</th>
            </tr>
        </thead>
        <tbody>${rowsHtml}</tbody>
    </table>`;
}

/** 生成物料在各仓库仓位的分布 Mock 数据 */
function _genMockDistribution(inv) {
    const warehouses = window.mockData.warehouses || [];
    const result = [];
    const now = new Date();

    /* 主仓库：来自 inventorySummary 本身 */
    result.push({
        warehouseName: inv.warehouseName,
        locationCode:  inv.locationCode,
        currentQty:    inv.currentQty,
        unit:          inv.unit,
        lockQty:       inv.lockQty || 0,
        batchNo:       inv.batchNo,
        expiryDate:    inv.expiryDate,
        status:        inv.status === 'warning' ? 'warning' : 'normal',
        lastCheckTime: _invFmtDate(new Date(now.getTime() - 2 * 24 * 3600 * 1000))
    });

    /* 如果还有其他仓库，模拟额外分布（最多再追加 2 条） */
    const otherWhs = warehouses.filter(w => w.warehouseName !== inv.warehouseName).slice(0, 2);
    const locSuffixes = ['A01','B02','C03'];
    otherWhs.forEach((wh, idx) => {
        const qty = Math.floor(inv.currentQty * 0.3 * (idx + 1));
        if (qty <= 0) return;
        result.push({
            warehouseName: wh.warehouseName,
            locationCode:  `${wh.warehouseCode}-${locSuffixes[idx + 1] || 'D04'}`,
            currentQty:    qty,
            unit:          inv.unit,
            lockQty:       0,
            batchNo:       inv.batchNo,
            expiryDate:    inv.expiryDate,
            status:        'normal',
            lastCheckTime: _invFmtDate(new Date(now.getTime() - (idx + 3) * 24 * 3600 * 1000))
        });
    });

    return result;
}

/* ============================================================
   【优化点2】出入库记录弹窗
   ============================================================ */
function showInvLogs(id) {
    const inv = (window.mockData.inventorySummary || []).find(x => x.id === id);
    if (!inv) return;

    window._invLogsAll      = _genMockInvLogs(inv);
    window._invLogsFiltered = window._invLogsAll.slice();
    window._invLogsPage     = 1;

    const html = `
        <div class="modal-header">
            <span class="modal-title">${inv.materialName} - 出入库记录明细</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <!-- 筛选栏 -->
            <div style="display:flex;gap:10px;align-items:center;margin-bottom:14px;flex-wrap:wrap">
                <select class="form-select" id="logsTypeFilter" style="width:120px"
                        onchange="filterInvLogs()">
                    <option value="">全部类型</option>
                    <option value="inbound">入库</option>
                    <option value="outbound">出库</option>
                    <option value="transfer">调拨</option>
                </select>
                <select class="form-select" id="logsDateFilter" style="width:120px"
                        onchange="filterInvLogs()">
                    <option value="">全部时间</option>
                    <option value="7">近7天</option>
                    <option value="30">近30天</option>
                    <option value="custom">自定义</option>
                </select>
                <div id="logsCustomDate" style="display:none;gap:6px;align-items:center">
                    <input type="date" class="form-input" id="logsDateFrom" style="width:130px"
                           onchange="filterInvLogs()">
                    <span style="color:#909399;font-size:13px">至</span>
                    <input type="date" class="form-input" id="logsDateTo" style="width:130px"
                           onchange="filterInvLogs()">
                </div>
                <input class="form-input" id="logsKeyword" placeholder="记录编号/关联单据"
                       style="width:170px" oninput="filterInvLogs()">
            </div>
            <!-- 记录表格 -->
            <div id="logsTableWrap"></div>
            <div id="logsPagination" style="padding:12px 0 0;display:flex;justify-content:flex-end;align-items:center;gap:8px"></div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>`;

    showModal(html, '900px');

    /* 监听自定义日期显示切换 */
    document.getElementById('logsDateFilter').addEventListener('change', function() {
        const custom = document.getElementById('logsCustomDate');
        if (custom) custom.style.display = this.value === 'custom' ? 'flex' : 'none';
    });

    _renderInvLogsTable();
}

function filterInvLogs() {
    const type    = document.getElementById('logsTypeFilter')?.value  || '';
    const dateRng = document.getElementById('logsDateFilter')?.value  || '';
    const keyword = (document.getElementById('logsKeyword')?.value    || '').trim().toLowerCase();
    const dateFrom= document.getElementById('logsDateFrom')?.value    || '';
    const dateTo  = document.getElementById('logsDateTo')?.value      || '';

    const now  = new Date();
    const cutoff = dateRng && dateRng !== 'custom'
        ? new Date(now.getTime() - parseInt(dateRng) * 24 * 3600 * 1000)
        : null;

    window._invLogsFiltered = window._invLogsAll.filter(l => {
        if (type    && l.opType !== type)                                 return false;
        if (cutoff  && new Date(l.opTime) < cutoff)                       return false;
        if (dateRng === 'custom' && dateFrom && l.opTime.slice(0,10) < dateFrom) return false;
        if (dateRng === 'custom' && dateTo   && l.opTime.slice(0,10) > dateTo)   return false;
        if (keyword && !l.logNo.toLowerCase().includes(keyword)
                    && !l.refNo.toLowerCase().includes(keyword))           return false;
        return true;
    });

    window._invLogsPage = 1;
    _renderInvLogsTable();
}

function invLogsChangePage(p) { window._invLogsPage = p; _renderInvLogsTable(); }

function _renderInvLogsTable() {
    const list     = window._invLogsFiltered;
    const total    = list.length;
    const pageSize = window._invLogsPageSize;
    const page     = window._invLogsPage;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const wrap = document.getElementById('logsTableWrap');
    if (!wrap) return;

    if (!pageData.length) {
        wrap.innerHTML = `<div style="text-align:center;padding:40px;color:#909399">该物料暂无出入库记录</div>`;
        document.getElementById('logsPagination').innerHTML = '';
        return;
    }

    /* 操作类型样式 */
    const typeStyle = {
        inbound:  { label: '入库', color: '#67c23a' },
        outbound: { label: '出库', color: '#f56c6c' },
        transfer: { label: '调拨', color: '#409eff' }
    };

    const rowsHtml = pageData.map(l => {
        const ts = typeStyle[l.opType] || { label: l.opType, color: '#909399' };
        const qtySign  = l.opType === 'outbound' ? '-' : (l.opType === 'transfer' ? '↔' : '+');
        const qtyColor = l.opType === 'outbound' ? '#f56c6c' : (l.opType === 'transfer' ? '#409eff' : '#67c23a');
        return `<tr>
            <td style="font-size:12px;color:#606266">${l.logNo}</td>
            <td><span style="color:${ts.color};font-weight:600">${ts.label}</span></td>
            <td>${l.opTime}</td>
            <td>${l.operator}</td>
            <td style="font-size:12px;color:#409eff">${l.refNo}</td>
            <td>${l.spec}</td>
            <td style="font-weight:600;color:${qtyColor}">${qtySign}${l.qty} ${l.unit}</td>
            <td>${l.afterQty} ${l.unit}</td>
            <td style="font-size:12px;color:#909399">${l.remark}</td>
        </tr>`;
    }).join('');

    wrap.innerHTML = `
        <table style="width:100%">
            <thead>
                <tr>
                    <th>记录编号</th>
                    <th>操作类型</th>
                    <th>操作时间</th>
                    <th>操作人</th>
                    <th>关联单据</th>
                    <th>规格</th>
                    <th>操作数量</th>
                    <th>操作后库存</th>
                    <th>备注</th>
                </tr>
            </thead>
            <tbody>${rowsHtml}</tbody>
        </table>`;

    /* 分页 */
    const totalPages = Math.ceil(total / pageSize);
    const pg = document.getElementById('logsPagination');
    if (!pg) return;
    let pgHtml = `<span style="color:#606266;font-size:13px">共 ${total} 条</span>`;
    if (totalPages > 1) {
        pgHtml += ` <button class="btn btn-default" style="padding:4px 10px;font-size:13px"
                            ${page<=1?'disabled':''} onclick="invLogsChangePage(${page-1})">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            pgHtml += `<button class="btn ${i===page?'btn-primary':'btn-default'}"
                               style="padding:4px 10px;font-size:13px"
                               onclick="invLogsChangePage(${i})">${i}</button>`;
        }
        pgHtml += `<button class="btn btn-default" style="padding:4px 10px;font-size:13px"
                           ${page>=totalPages?'disabled':''} onclick="invLogsChangePage(${page+1})">下一页</button>`;
    }
    pg.innerHTML = pgHtml;
}

/** 生成含记录编号/关联单据的完整出入库 Mock 日志（15条，覆盖入库/出库/调拨） */
function _genMockInvLogs(inv) {
    const operators = ['张三', '李四', '王五', '赵六'];
    const now       = new Date();

    /* 预定义业务场景模板 */
    const templates = [
        { opType:'inbound',  refPfx:'PO', remark:'采购订单入库',    qtyRate: 0.4  },
        { opType:'outbound', refPfx:'LR', remark:'后厨领料出库',    qtyRate: -0.15 },
        { opType:'inbound',  refPfx:'PO', remark:'紧急采购补货',    qtyRate: 0.3  },
        { opType:'outbound', refPfx:'LR', remark:'后厨领料出库',    qtyRate: -0.12 },
        { opType:'transfer', refPfx:'TR', remark:'跨仓库调拨',      qtyRate: 0.1  },
        { opType:'outbound', refPfx:'LR', remark:'食材领料出库',    qtyRate: -0.08 },
        { opType:'inbound',  refPfx:'PO', remark:'月度定期采购入库', qtyRate: 0.5  },
        { opType:'outbound', refPfx:'LR', remark:'后厨领料出库',    qtyRate: -0.2  },
        { opType:'inbound',  refPfx:'RT', remark:'退货重新入库',    qtyRate: 0.05 },
        { opType:'outbound', refPfx:'LR', remark:'宴会用料出库',    qtyRate: -0.25 },
        { opType:'transfer', refPfx:'TR', remark:'仓位调整调拨',    qtyRate: 0.08 },
        { opType:'outbound', refPfx:'LR', remark:'日常领料出库',    qtyRate: -0.1  },
        { opType:'inbound',  refPfx:'PO', remark:'周计划采购入库',  qtyRate: 0.35 },
        { opType:'outbound', refPfx:'LR', remark:'后厨备料出库',    qtyRate: -0.18 },
        { opType:'inbound',  refPfx:'PO', remark:'专项补货入库',    qtyRate: 0.2  }
    ];

    let runningQty = inv.currentQty;
    const logs = [];

    templates.forEach((tpl, idx) => {
        const daysAgo = idx * 2;
        const d       = new Date(now.getTime() - daysAgo * 24 * 3600 * 1000 - idx * 3600 * 1000);
        const baseQty = Math.max(1, Math.round(Math.abs(tpl.qtyRate) * (inv.currentQty || 20)));
        const qty     = tpl.opType === 'outbound' ? baseQty : baseQty;
        const refSeq  = String(20260301 + idx).slice(4) + String(idx + 1).padStart(2,'0');

        logs.push({
            logNo:    `LOG-${d.toISOString().slice(0,10).replace(/-/g,'')}-${String(idx+1).padStart(3,'0')}`,
            opType:   tpl.opType,
            opTime:   d.toLocaleString('sv-SE').replace('T',' ').slice(0, 16),
            operator: operators[idx % operators.length],
            refNo:    `${tpl.refPfx}-2026${refSeq}`,
            spec:     inv.spec,
            unit:     inv.unit,
            qty,
            afterQty: Math.max(0, runningQty),
            remark:   tpl.remark
        });

        /* 反向累积让每条记录的 afterQty 合理递增/递减 */
        runningQty = runningQty + (tpl.opType === 'outbound' ? qty : -qty);
        if (runningQty < 0) runningQty = 0;
    });

    /* 按时间倒序返回（最新在前） */
    return logs.reverse();
}

/* ============================================================
   工具函数
   ============================================================ */
function _invFmtDate(d) {
    return d.toLocaleString('sv-SE').replace('T', ' ').slice(0, 16);
}
