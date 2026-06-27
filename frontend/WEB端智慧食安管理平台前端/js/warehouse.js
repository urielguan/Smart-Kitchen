/**
 * warehouse.js — 仓库信息管理模块
 *
 * 实现 8 个核心功能：
 *  1. 新增仓库   2. 编辑仓库   3. 查看仓库列表   4. 查看仓库详情
 *  5. 新增仓位   6. 编辑仓位   7. 查看仓库下仓位列表   8. 查看仓位详情
 *
 * 其余子标签（入库单/出库单/盘点单）保持原有逻辑不变。
 */

/* ============================================================
   常量 & 辅助映射
   ============================================================ */
const WAREHOUSE_TYPE_MAP = { normal: '常温库', cold: '冷藏库', freeze: '冷冻库', dry: '干货库' };
const WAREHOUSE_STATUS_MAP = { active: '启用', inactive: '停用', maintenance: '维护中' };
const LOCATION_STATUS_MAP  = { available: '可用', occupied: '占用', maintenance: '维护中' };
const TEMP_STATUS_MAP      = { normal: '正常', warning: '预警', alert: '告警' };

function whTypeName(type)   { return WAREHOUSE_TYPE_MAP[type]   || type; }
function whStatusName(s)    { return WAREHOUSE_STATUS_MAP[s]    || s; }
function locStatusName(s)   { return LOCATION_STATUS_MAP[s]     || s; }
function tempStatusName(s)  { return TEMP_STATUS_MAP[s]         || s; }

function whStatusTag(s) {
    const map = { active: 'success', inactive: 'danger', maintenance: 'warning' };
    const cls = map[s] || 'info';
    return `<span class="tag tag-${cls}">${whStatusName(s)}</span>`;
}
function locStatusTag(s) {
    const map = { available: 'success', occupied: 'info', maintenance: 'warning' };
    const cls = map[s] || 'info';
    return `<span class="tag tag-${cls}">${locStatusName(s)}</span>`;
}
function tempTag(s) {
    const map = { normal: 'success', warning: 'warning', alert: 'danger' };
    const cls = map[s] || 'info';
    return `<span class="tag tag-${cls}">${tempStatusName(s)}</span>`;
}

/* 分页状态（显式挂载到 window，确保跨文件可访问） */
window.whPage = 1;
const WH_PAGE_SIZE = 10;
window.locPage = 1;
const LOC_PAGE_SIZE = 10;
window.currentLocWarehouseId = null; // 当前查看仓位的仓库 id

/* ============================================================
   仓库管理主页入口
   ============================================================ */
function renderWarehousePage(content) {
    content.innerHTML = `
        <div class="tabs" id="warehouseTabs">
            <div class="tab-item ${currentWarehouseTab === 'list'      ? 'active' : ''}" onclick="switchWarehouseTab('list')">仓库列表</div>
            <div class="tab-item ${currentWarehouseTab === 'inbound'   ? 'active' : ''}" onclick="switchWarehouseTab('inbound')">入库单</div>
            <div class="tab-item ${currentWarehouseTab === 'outbound'  ? 'active' : ''}" onclick="switchWarehouseTab('outbound')">出库单</div>
            <div class="tab-item ${currentWarehouseTab === 'stocktake' ? 'active' : ''}" onclick="switchWarehouseTab('stocktake')">盘点单</div>
        </div>
        <div id="warehouseTabContent"></div>
    `;
    renderWarehouseTabContent();
}

function switchWarehouseTab(tab) {
    currentWarehouseTab = tab;
    const labelMap = { list: '仓库列表', inbound: '入库单', outbound: '出库单', stocktake: '盘点单' };
    document.querySelectorAll('#warehouseTabs .tab-item').forEach(t => {
        t.classList.toggle('active', t.textContent.trim() === labelMap[tab]);
    });
    renderWarehouseTabContent();
}

function renderWarehouseTabContent() {
    const container = document.getElementById('warehouseTabContent');
    if      (currentWarehouseTab === 'list')      renderWarehouseList(container);
    else if (currentWarehouseTab === 'inbound')   renderInboundOrders(container);
    else if (currentWarehouseTab === 'outbound')  renderOutboundOrders(container);
    else if (currentWarehouseTab === 'stocktake') renderStocktakeOrders(container);
}

/* ============================================================
   1 & 3. 仓库列表（含搜索、分页）
   ============================================================ */
function renderWarehouseList(container, filteredData) {
    const all  = filteredData || mockData.warehouses;
    const total = all.length;
    const totalPages = Math.max(1, Math.ceil(total / WH_PAGE_SIZE));
    if (whPage > totalPages) whPage = totalPages;
    const list = all.slice((whPage - 1) * WH_PAGE_SIZE, whPage * WH_PAGE_SIZE);

    const activeCount = mockData.warehouses.filter(w => w.status === 'active').length;
    const warnCount   = mockData.warehouses.filter(w => w.humidityStatus !== 'normal' || w.tempStatus !== 'normal').length;

    container.innerHTML = `
        <div class="stats-cards">
            <div class="stat-card">
                <div class="stat-card-title">仓库总数</div>
                <div class="stat-card-value">${mockData.warehouses.length}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">启用中</div>
                <div class="stat-card-value" style="color:#67c23a">${activeCount}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">环境预警</div>
                <div class="stat-card-value" style="color:#e6a23c">${warnCount}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">仓位总数</div>
                <div class="stat-card-value" style="color:#409eff">${mockData.locations.length}</div>
            </div>
        </div>

        <div class="toolbar">
            <div class="toolbar-row">
                <input type="text" id="whSearchName" placeholder="仓库名称/编码">
                <select id="whSearchType">
                    <option value="">全部类型</option>
                    <option value="cold">冷藏库</option>
                    <option value="freeze">冷冻库</option>
                    <option value="normal">常温库</option>
                    <option value="dry">干货库</option>
                </select>
                <select id="whSearchStatus">
                    <option value="">全部状态</option>
                    <option value="active">启用</option>
                    <option value="inactive">停用</option>
                    <option value="maintenance">维护中</option>
                </select>
                <button class="btn btn-primary" onclick="searchWarehouses()">搜索</button>
                <button class="btn btn-default"  onclick="resetWarehouseSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openWarehouseModal()">+ 新增仓库</button>
            </div>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>仓库编码</th><th>仓库名称</th><th>类型</th><th>负责人</th>
                        <th>联系方式</th><th>容量使用</th><th>仓位</th><th>温度</th><th>状态</th><th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    ${list.length === 0 ? `<tr><td colspan="10" style="text-align:center;color:#909399;padding:30px">暂无数据</td></tr>` :
                    list.map(w => {
                        const usePct = w.maxCapacity ? Math.round(w.usedCapacity / w.maxCapacity * 100) : 0;
                        const tempStr = w.temperature !== null ? `${w.temperature}℃` : '-';
                        return `<tr>
                            <td>${w.warehouseCode}</td>
                            <td>${w.warehouseName}</td>
                            <td>${whTypeName(w.warehouseType)}</td>
                            <td>${w.managerName}</td>
                            <td>${w.managerPhone}</td>
                            <td>
                                <div style="display:flex;align-items:center;gap:6px">
                                    <div style="flex:1;background:#ebeef5;border-radius:4px;height:8px;min-width:60px">
                                        <div style="width:${usePct}%;background:${usePct>85?'#f56c6c':usePct>60?'#e6a23c':'#67c23a'};height:8px;border-radius:4px"></div>
                                    </div>
                                    <span style="font-size:12px;color:#606266">${usePct}%</span>
                                </div>
                            </td>
                            <td>${w.positionUsed}/${w.positionTotal}</td>
                            <td>${tempStr} ${tempTag(w.tempStatus)}</td>
                            <td>${whStatusTag(w.status)}</td>
                            <td>
                                <div class="action-btns">
                                    <button class="btn-link" onclick="showWarehouseDetail(${w.id})">详情</button>
                                    <button class="btn-link" onclick="openWarehouseModal(${w.id})">编辑</button>
                                    <button class="btn-link" onclick="openLocationListModal(${w.id})">仓位</button>
                                    <button class="btn-link danger" onclick="deleteWarehouse(${w.id})">删除</button>
                                </div>
                            </td>
                        </tr>`;
                    }).join('')}
                </tbody>
            </table>
            <div class="pagination">
                <span style="color:#909399;font-size:13px">共 ${total} 条</span>
                <div style="display:flex;gap:4px;align-items:center">
                    <button class="btn btn-default" style="padding:4px 10px;font-size:13px"
                        onclick="changeWhPage(${whPage-1})" ${whPage<=1?'disabled':''}>上一页</button>
                    <span style="font-size:13px;color:#606266">${whPage} / ${totalPages}</span>
                    <button class="btn btn-default" style="padding:4px 10px;font-size:13px"
                        onclick="changeWhPage(${whPage+1})" ${whPage>=totalPages?'disabled':''}>下一页</button>
                </div>
            </div>
        </div>
    `;
}

function changeWhPage(p) {
    whPage = p;
    renderWarehouseTabContent();
}

function searchWarehouses() {
    const kw     = document.getElementById('whSearchName').value.trim().toLowerCase();
    const type   = document.getElementById('whSearchType').value;
    const status = document.getElementById('whSearchStatus').value;
    whPage = 1;
    const filtered = mockData.warehouses.filter(w =>
        (!kw     || w.warehouseName.toLowerCase().includes(kw) || w.warehouseCode.toLowerCase().includes(kw)) &&
        (!type   || w.warehouseType === type) &&
        (!status || w.status === status)
    );
    renderWarehouseList(document.getElementById('warehouseTabContent'), filtered);
    if (document.getElementById('whSearchName'))   document.getElementById('whSearchName').value   = kw;
    if (document.getElementById('whSearchType'))   document.getElementById('whSearchType').value   = type;
    if (document.getElementById('whSearchStatus')) document.getElementById('whSearchStatus').value = status;
}

function resetWarehouseSearch() {
    whPage = 1;
    renderWarehouseList(document.getElementById('warehouseTabContent'));
}

function deleteWarehouse(id) {
    const w = mockData.warehouses.find(x => x.id === id);
    if (!w) return;
    const hasStock = mockData.locations.some(l => l.warehouseId === id && l.usedCapacity > 0);
    if (hasStock) { showToast('该仓库下仍有库存，无法删除', 'error'); return; }
    if (confirm(`确定删除仓库「${w.warehouseName}」吗？`)) {
        mockData.warehouses.splice(mockData.warehouses.findIndex(x => x.id === id), 1);
        mockData.locations = mockData.locations.filter(l => l.warehouseId !== id);
        renderWarehouseTabContent();
        showToast('删除成功');
    }
}

/* ============================================================
   2. 新增/编辑仓库弹窗
   ============================================================ */
function openWarehouseModal(id) {
    editingWarehouseId = id || null;
    const w = id ? mockData.warehouses.find(x => x.id === id) : null;
    const title = id ? '编辑仓库' : '新增仓库';
    const isEdit = !!id;

    const html = `
        <div class="modal-header">
            <div class="modal-title">${title}</div>
            <span class="modal-close" onclick="closeModal()">×</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden;padding:24px">
            <div class="form-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));box-sizing:border-box;width:100%;gap:16px 20px">
                <div class="form-item">
                    <label class="form-label required">仓库编码</label>
                    <input class="form-input" id="fWhCode" value="${w ? w.warehouseCode : ''}" placeholder="如：WH-005">
                </div>
                <div class="form-item">
                    <label class="form-label required">仓库名称</label>
                    <input class="form-input" id="fWhName" value="${w ? w.warehouseName : ''}" placeholder="请输入仓库名称">
                </div>
                <div class="form-item">
                    <label class="form-label required">仓库类型</label>
                    <select class="form-select" id="fWhType">
                        <option value="cold"    ${w && w.warehouseType==='cold'    ? 'selected':''}>冷藏库</option>
                        <option value="freeze"  ${w && w.warehouseType==='freeze'  ? 'selected':''}>冷冻库</option>
                        <option value="normal"  ${w && w.warehouseType==='normal'  ? 'selected':''}>常温库</option>
                        <option value="dry"     ${w && w.warehouseType==='dry'     ? 'selected':''}>干货库</option>
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label">最大容量</label>
                    <div style="display:flex;gap:6px">
                        <input class="form-input" type="number" id="fWhCapacity" value="${w ? w.maxCapacity : ''}" placeholder="数量" style="flex:1">
                        <select class="form-select" id="fWhCapUnit" style="width:90px">
                            <option value="平方米" ${w && w.capacityUnit==='平方米' ? 'selected':''}>平方米</option>
                            <option value="立方米" ${w && w.capacityUnit==='立方米' ? 'selected':''}>立方米</option>
                        </select>
                    </div>
                </div>
                <div class="form-item" style="grid-column:span 2">
                    <label class="form-label required">仓库位置</label>
                    <input class="form-input" id="fWhLocation" value="${w ? (w.location||'') : ''}" placeholder="如：食堂后楼一层A区">
                </div>
                <div class="form-item">
                    <label class="form-label required">负责人</label>
                    <input class="form-input" id="fWhManager" value="${w ? w.managerName : ''}" placeholder="请输入负责人姓名">
                </div>
                <div class="form-item">
                    <label class="form-label">负责人联系方式</label>
                    <input class="form-input" id="fWhPhone" value="${w ? w.managerPhone : ''}" placeholder="请输入手机号">
                </div>
                <div class="form-item" style="grid-column:span 2">
                    <label class="form-label">温度范围（℃）</label>
                    <div style="display:flex;gap:8px;align-items:center">
                        <input class="form-input" type="number" id="fWhTempMin" value="${w ? (w.tempMin??'') : ''}" placeholder="最低温度" style="flex:1">
                        <span style="color:#909399;flex-shrink:0">~</span>
                        <input class="form-input" type="number" id="fWhTempMax" value="${w ? (w.tempMax??'') : ''}" placeholder="最高温度" style="flex:1">
                    </div>
                </div>
                <div class="form-item" style="grid-column:span 2">
                    <label class="form-label">湿度范围（%）</label>
                    <div style="display:flex;gap:8px;align-items:center">
                        <input class="form-input" type="number" id="fWhHumMin" value="${w ? (w.humidityMin??'') : ''}" placeholder="最低湿度" style="flex:1">
                        <span style="color:#909399;flex-shrink:0">~</span>
                        <input class="form-input" type="number" id="fWhHumMax" value="${w ? (w.humidityMax??'') : ''}" placeholder="最高湿度" style="flex:1">
                    </div>
                </div>
                ${isEdit ? `
                <div class="form-item">
                    <label class="form-label">仓库状态</label>
                    <select class="form-select" id="fWhStatus">
                        <option value="active"      ${w && w.status==='active'      ? 'selected':''}>启用</option>
                        <option value="inactive"    ${w && w.status==='inactive'    ? 'selected':''}>停用</option>
                        <option value="maintenance" ${w && w.status==='maintenance' ? 'selected':''}>维护中</option>
                    </select>
                </div>
                <div class="form-item"></div>` : ''}
                <div class="form-item" style="grid-column:span 2">
                    <label class="form-label">备注</label>
                    <textarea class="form-textarea" id="fWhRemark" rows="2" placeholder="请输入备注">${w ? (w.remark||'') : ''}</textarea>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveWarehouse()">确认</button>
        </div>
    `;
    showModal(html, 'min(680px, 92vw)');
}

function saveWarehouse() {
    const code    = document.getElementById('fWhCode').value.trim();
    const name    = document.getElementById('fWhName').value.trim();
    const manager = document.getElementById('fWhManager').value.trim();
    const loc     = document.getElementById('fWhLocation').value.trim();
    if (!code || !name || !manager || !loc) {
        showToast('请填写必填项（编码、名称、位置、负责人）', 'error');
        return;
    }
    // 编码唯一性校验
    const dupCode = mockData.warehouses.find(x => x.warehouseCode === code && x.id !== editingWarehouseId);
    if (dupCode) { showToast('仓库编码已存在，请更换', 'error'); return; }

    if (editingWarehouseId) {
        const w = mockData.warehouses.find(x => x.id === editingWarehouseId);
        if (w) {
            w.warehouseCode = code;
            w.warehouseName = name;
            w.warehouseType = document.getElementById('fWhType').value;
            w.maxCapacity   = parseInt(document.getElementById('fWhCapacity').value) || w.maxCapacity;
            w.capacityUnit  = document.getElementById('fWhCapUnit').value;
            w.location      = loc;
            w.managerName   = manager;
            w.managerPhone  = document.getElementById('fWhPhone').value.trim();
            const statusEl  = document.getElementById('fWhStatus');
            if (statusEl) w.status = statusEl.value;
            w.remark        = document.getElementById('fWhRemark').value.trim();
            w.tempMin       = document.getElementById('fWhTempMin').value !== '' ? parseFloat(document.getElementById('fWhTempMin').value) : null;
            w.tempMax       = document.getElementById('fWhTempMax').value !== '' ? parseFloat(document.getElementById('fWhTempMax').value) : null;
            w.humidityMin   = document.getElementById('fWhHumMin').value  !== '' ? parseFloat(document.getElementById('fWhHumMin').value)  : null;
            w.humidityMax   = document.getElementById('fWhHumMax').value  !== '' ? parseFloat(document.getElementById('fWhHumMax').value)  : null;
            // 同步更新 locations 中 warehouseName
            mockData.locations.filter(l => l.warehouseId === w.id).forEach(l => l.warehouseName = name);
        }
    } else {
        const newId = Date.now();
        mockData.warehouses.push({
            id: newId,
            warehouseCode: code,
            warehouseName: name,
            warehouseType: document.getElementById('fWhType').value,
            maxCapacity:   parseInt(document.getElementById('fWhCapacity').value) || 100,
            capacityUnit:  document.getElementById('fWhCapUnit').value,
            usedCapacity:  0,
            location:      loc,
            managerId:     null,
            managerName:   manager,
            managerPhone:  document.getElementById('fWhPhone').value.trim(),
            status:        'active',
            remark:        document.getElementById('fWhRemark').value.trim(),
            tempMin:       document.getElementById('fWhTempMin').value !== '' ? parseFloat(document.getElementById('fWhTempMin').value) : null,
            tempMax:       document.getElementById('fWhTempMax').value !== '' ? parseFloat(document.getElementById('fWhTempMax').value) : null,
            humidityMin:   document.getElementById('fWhHumMin').value  !== '' ? parseFloat(document.getElementById('fWhHumMin').value)  : null,
            humidityMax:   document.getElementById('fWhHumMax').value  !== '' ? parseFloat(document.getElementById('fWhHumMax').value)  : null,
            createdAt:     new Date().toLocaleString('sv').replace('T',' '),
            positionTotal: 0, positionUsed: 0, positionIdle: 0,
            temperature: null, humidity: null,
            tempStatus: 'normal', humidityStatus: 'normal'
        });
    }
    closeModal();
    renderWarehouseTabContent();
    showToast(editingWarehouseId ? '编辑成功' : '新增成功');
}

/* ============================================================
   4. 仓库详情弹窗
   ============================================================ */
function showWarehouseDetail(id) {
    const w = mockData.warehouses.find(x => x.id === id);
    if (!w) return;
    const locs = mockData.locations.filter(l => l.warehouseId === id);
    const usePct = w.maxCapacity ? Math.round(w.usedCapacity / w.maxCapacity * 100) : 0;

    const html = `
        <div class="modal-header">
            <div class="modal-title">仓库详情 — ${w.warehouseName}</div>
            <span class="modal-close" onclick="closeModal()">×</span>
        </div>
        <div class="modal-body">
            <!-- 基础信息 -->
            <div class="detail-section">
                <div class="detail-section-title">基础信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">仓库编码</span><span class="detail-value">${w.warehouseCode}</span></div>
                    <div class="detail-item"><span class="detail-label">仓库名称</span><span class="detail-value">${w.warehouseName}</span></div>
                    <div class="detail-item"><span class="detail-label">仓库类型</span><span class="detail-value">${whTypeName(w.warehouseType)}</span></div>
                    <div class="detail-item"><span class="detail-label">仓库位置</span><span class="detail-value">${w.location||'-'}</span></div>
                    <div class="detail-item"><span class="detail-label">负责人</span><span class="detail-value">${w.managerName}</span></div>
                    <div class="detail-item"><span class="detail-label">联系方式</span><span class="detail-value">${w.managerPhone||'-'}</span></div>
                    <div class="detail-item"><span class="detail-label">仓库状态</span><span class="detail-value">${whStatusTag(w.status)}</span></div>
                    <div class="detail-item"><span class="detail-label">创建时间</span><span class="detail-value">${w.createdAt||'-'}</span></div>
                    <div class="detail-item"><span class="detail-label">温度范围</span><span class="detail-value">${w.tempMin!=null?w.tempMin+'℃':'-'} ~ ${w.tempMax!=null?w.tempMax+'℃':'-'}</span></div>
                    <div class="detail-item"><span class="detail-label">湿度范围</span><span class="detail-value">${w.humidityMin!=null?w.humidityMin+'%':'-'} ~ ${w.humidityMax!=null?w.humidityMax+'%':'-'}</span></div>
                    <div class="detail-item" style="grid-column:span 2"><span class="detail-label">备注</span><span class="detail-value">${w.remark||'-'}</span></div>
                </div>
            </div>

            <!-- 实时指标 -->
            <div class="detail-section">
                <div class="detail-section-title">实时环境指标</div>
                <div class="detail-grid">
                    <div class="detail-item">
                        <span class="detail-label">当前温度</span>
                        <span class="detail-value">${w.temperature !== null ? w.temperature+'℃' : '—'} ${tempTag(w.tempStatus)}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">当前湿度</span>
                        <span class="detail-value">${w.humidity !== null ? w.humidity+'%' : '—'} ${tempTag(w.humidityStatus)}</span>
                    </div>
                </div>
            </div>

            <!-- 容量 & 仓位统计 -->
            <div class="detail-section">
                <div class="detail-section-title">容量 & 仓位统计</div>
                <div class="detail-grid">
                    <div class="detail-item">
                        <span class="detail-label">最大容量</span>
                        <span class="detail-value">${w.maxCapacity} ${w.capacityUnit}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">已用容量</span>
                        <span class="detail-value">${w.usedCapacity} ${w.capacityUnit}（${usePct}%）</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">仓位总数</span>
                        <span class="detail-value">${w.positionTotal} 个</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">已占用/空闲</span>
                        <span class="detail-value">${w.positionUsed} / ${w.positionIdle}</span>
                    </div>
                </div>
            </div>

            <!-- 仓位列表 -->
            <div class="detail-section">
                <div class="detail-section-title" style="display:flex;justify-content:space-between;align-items:center">
                    <span>该仓库下的仓位（${locs.length}个）</span>
                    <button class="btn btn-primary" style="padding:4px 12px;font-size:13px"
                        onclick="closeModal();openLocationModal(null,${w.id})">+ 新增仓位</button>
                </div>
                ${locs.length === 0 ? `<div style="text-align:center;color:#909399;padding:20px">暂无仓位，点击上方按钮新增</div>` : `
                <table style="margin-top:10px">
                    <thead>
                        <tr><th>仓位编码</th><th>仓位名称</th><th>类型</th><th>使用情况</th><th>状态</th><th>操作</th></tr>
                    </thead>
                    <tbody>
                        ${locs.map(l => {
                            const pct = l.capacity ? Math.round(l.usedCapacity/l.capacity*100) : 0;
                            return `<tr>
                                <td>${l.locationCode}</td>
                                <td>${l.locationName}</td>
                                <td>${l.locationType}</td>
                                <td>${l.usedCapacity}/${l.capacity}${l.capacityUnit} (${pct}%)</td>
                                <td>${locStatusTag(l.status)}</td>
                                <td>
                                    <div class="action-btns">
                                        <button class="btn-link" onclick="closeModal();showLocationDetail(${l.id})">详情</button>
                                        <button class="btn-link" onclick="closeModal();openLocationModal(${l.id})">编辑</button>
                                    </div>
                                </td>
                            </tr>`;
                        }).join('')}
                    </tbody>
                </table>`}
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
            <button class="btn btn-primary" onclick="closeModal();openWarehouseModal(${w.id})">编辑仓库</button>
        </div>
    `;
    showModal(html, '760px');
}

/* ============================================================
   7. 仓位列表弹窗（仓库下的仓位，带分页与搜索）
   ============================================================ */
function openLocationListModal(warehouseId) {
    currentLocWarehouseId = warehouseId;
    locPage = 1;
    renderLocationListModal(warehouseId);
}

function renderLocationListModal(warehouseId, keyword, statusFilter) {
    const w   = mockData.warehouses.find(x => x.id === warehouseId);
    let locs  = mockData.locations.filter(l => l.warehouseId === warehouseId);
    if (keyword)      locs = locs.filter(l => l.locationCode.includes(keyword) || l.locationName.includes(keyword));
    if (statusFilter) locs = locs.filter(l => l.status === statusFilter);
    const total = locs.length;
    const totalPages = Math.max(1, Math.ceil(total / LOC_PAGE_SIZE));
    if (locPage > totalPages) locPage = totalPages;
    const pageList = locs.slice((locPage-1)*LOC_PAGE_SIZE, locPage*LOC_PAGE_SIZE);

    const html = `
        <div class="modal-header">
            <div class="modal-title">仓位列表 — ${w ? w.warehouseName : ''}</div>
            <span class="modal-close" onclick="closeModal()">×</span>
        </div>
        <div class="modal-body">
            <div class="toolbar" style="margin-bottom:10px">
                <div class="toolbar-row">
                    <input type="text" id="locSearchKw" placeholder="仓位编码/名称" value="${keyword||''}">
                    <select id="locSearchStatus">
                        <option value="">全部状态</option>
                        <option value="available"   ${statusFilter==='available'   ?'selected':''}>可用</option>
                        <option value="occupied"    ${statusFilter==='occupied'    ?'selected':''}>占用</option>
                        <option value="maintenance" ${statusFilter==='maintenance' ?'selected':''}>维护中</option>
                    </select>
                    <button class="btn btn-primary" onclick="searchLocations(${warehouseId})">搜索</button>
                    <button class="btn btn-default"  onclick="resetLocSearch(${warehouseId})">重置</button>
                    <button class="btn btn-primary toolbar-right" onclick="closeModal();openLocationModal(null,${warehouseId})">+ 新增仓位</button>
                </div>
            </div>
            <div class="table-container" style="margin-top:0">
                <table>
                    <thead>
                        <tr>
                            <th>仓位编码</th><th>仓位名称</th><th>类型</th><th>区/货架/层</th>
                            <th>容量使用</th><th>适用物料</th><th>状态</th><th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${pageList.length === 0 ? `<tr><td colspan="8" style="text-align:center;color:#909399;padding:20px">暂无仓位数据</td></tr>` :
                        pageList.map(l => {
                            const pct = l.capacity ? Math.round(l.usedCapacity/l.capacity*100) : 0;
                            return `<tr>
                                <td>${l.locationCode}</td>
                                <td>${l.locationName}</td>
                                <td>${l.locationType}</td>
                                <td>${l.areaCode||'-'} / ${l.shelfCode||'-'} / ${l.levelCode||'-'}</td>
                                <td>${l.usedCapacity}/${l.capacity}${l.capacityUnit} (${pct}%)</td>
                                <td>${l.materialTypes||'-'}</td>
                                <td>${locStatusTag(l.status)}</td>
                                <td>
                                    <div class="action-btns">
                                        <button class="btn-link" onclick="closeModal();showLocationDetail(${l.id})">详情</button>
                                        <button class="btn-link" onclick="closeModal();openLocationModal(${l.id})">编辑</button>
                                        <button class="btn-link danger" onclick="deleteLocation(${l.id},${warehouseId})">删除</button>
                                    </div>
                                </td>
                            </tr>`;
                        }).join('')}
                    </tbody>
                </table>
                <div class="pagination">
                    <span style="color:#909399;font-size:13px">共 ${total} 条</span>
                    <div style="display:flex;gap:4px;align-items:center">
                        <button class="btn btn-default" style="padding:4px 10px;font-size:13px"
                            onclick="changeLocPage(${locPage-1},${warehouseId})" ${locPage<=1?'disabled':''}>上一页</button>
                        <span style="font-size:13px;color:#606266">${locPage} / ${totalPages}</span>
                        <button class="btn btn-default" style="padding:4px 10px;font-size:13px"
                            onclick="changeLocPage(${locPage+1},${warehouseId})" ${locPage>=totalPages?'disabled':''}>下一页</button>
                    </div>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `;
    showModal(html, '860px');
}

function searchLocations(warehouseId) {
    locPage = 1;
    const kw     = (document.getElementById('locSearchKw')     || {}).value || '';
    const status = (document.getElementById('locSearchStatus') || {}).value || '';
    renderLocationListModal(warehouseId, kw.trim(), status);
}

function resetLocSearch(warehouseId) {
    locPage = 1;
    renderLocationListModal(warehouseId);
}

function changeLocPage(p, warehouseId) {
    locPage = p;
    const kw     = (document.getElementById('locSearchKw')     || {}).value || '';
    const status = (document.getElementById('locSearchStatus') || {}).value || '';
    renderLocationListModal(warehouseId, kw.trim(), status);
}

function deleteLocation(locId, warehouseId) {
    const l = mockData.locations.find(x => x.id === locId);
    if (!l) return;
    if (l.usedCapacity > 0) { showToast('该仓位仍有库存，无法删除', 'error'); return; }
    if (confirm(`确定删除仓位「${l.locationName}」吗？`)) {
        mockData.locations.splice(mockData.locations.findIndex(x => x.id === locId), 1);
        // 更新仓库统计
        const w = mockData.warehouses.find(x => x.id === warehouseId);
        if (w) {
            w.positionTotal = Math.max(0, w.positionTotal - 1);
            if (l.status !== 'available') w.positionUsed = Math.max(0, w.positionUsed - 1);
            else w.positionIdle = Math.max(0, w.positionIdle - 1);
        }
        renderLocationListModal(warehouseId);
        showToast('删除成功');
    }
}

/* ============================================================
   5 & 6. 新增/编辑仓位弹窗
   ============================================================ */
function openLocationModal(locId, warehouseId) {
    const l = locId ? mockData.locations.find(x => x.id === locId) : null;
    const bindWhId = warehouseId || (l ? l.warehouseId : null);
    const title = locId ? '编辑仓位' : '新增仓位';

    const whOptions = mockData.warehouses.map(w =>
        `<option value="${w.id}" ${bindWhId === w.id ? 'selected':''}>${w.warehouseName}</option>`
    ).join('');

    const html = `
        <div class="modal-header">
            <div class="modal-title">${title}</div>
            <span class="modal-close" onclick="closeModal()">×</span>
        </div>
        <div class="modal-body">
            <div class="form-grid">
                <div class="form-item">
                    <label class="form-label required">所属仓库</label>
                    <select class="form-select" id="fLocWhId" ${locId?'disabled':''}>
                        <option value="">请选择仓库</option>
                        ${whOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">仓位编码</label>
                    <input class="form-input" id="fLocCode" value="${l ? l.locationCode : ''}" placeholder="如：WH001-A03">
                </div>
                <div class="form-item">
                    <label class="form-label required">仓位名称</label>
                    <input class="form-input" id="fLocName" value="${l ? l.locationName : ''}" placeholder="如：A区03号货位">
                </div>
                <div class="form-item">
                    <label class="form-label">仓位类型</label>
                    <input class="form-input" id="fLocType" value="${l ? l.locationType : ''}" placeholder="如：货架位、托盘位">
                </div>
                <div class="form-item">
                    <label class="form-label">区编号</label>
                    <input class="form-input" id="fLocArea"  value="${l ? (l.areaCode||'')  : ''}" placeholder="如：A">
                </div>
                <div class="form-item">
                    <label class="form-label">货架编号</label>
                    <input class="form-input" id="fLocShelf" value="${l ? (l.shelfCode||'') : ''}" placeholder="如：01">
                </div>
                <div class="form-item">
                    <label class="form-label">层编号</label>
                    <input class="form-input" id="fLocLevel" value="${l ? (l.levelCode||'') : ''}" placeholder="如：01">
                </div>
                <div class="form-item">
                    <label class="form-label">容量</label>
                    <div style="display:flex;gap:6px">
                        <input class="form-input" type="number" id="fLocCap" value="${l ? l.capacity : ''}" placeholder="数量" style="flex:1">
                        <select class="form-select" id="fLocCapUnit" style="width:80px">
                            <option value="kg"  ${l && l.capacityUnit==='kg'  ? 'selected':''}>kg</option>
                            <option value="件"  ${l && l.capacityUnit==='件'  ? 'selected':''}>件</option>
                            <option value="箱"  ${l && l.capacityUnit==='箱'  ? 'selected':''}>箱</option>
                        </select>
                    </div>
                </div>
                <div class="form-item">
                    <label class="form-label">适用物料类型</label>
                    <input class="form-input" id="fLocMatTypes" value="${l ? (l.materialTypes||'') : ''}" placeholder="如：蔬菜,水果">
                </div>
                ${locId ? `
                <div class="form-item">
                    <label class="form-label">仓位状态</label>
                    <select class="form-select" id="fLocStatus">
                        <option value="available"   ${l && l.status==='available'   ? 'selected':''}>可用</option>
                        <option value="occupied"    ${l && l.status==='occupied'    ? 'selected':''}>占用</option>
                        <option value="maintenance" ${l && l.status==='maintenance' ? 'selected':''}>维护中</option>
                    </select>
                </div>` : ''}
                <div class="form-item" style="grid-column:span 2">
                    <label class="form-label">备注</label>
                    <textarea class="form-textarea" id="fLocRemark" rows="2" placeholder="请输入备注">${l ? (l.remark||'') : ''}</textarea>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveLocation(${locId||'null'},${bindWhId||'null'})">确认</button>
        </div>
    `;
    showModal(html, '600px');
}

function saveLocation(locId, bindWhId) {
    const whIdEl  = document.getElementById('fLocWhId');
    const whId    = locId ? bindWhId : (whIdEl ? parseInt(whIdEl.value) : null);
    const code    = document.getElementById('fLocCode').value.trim();
    const name    = document.getElementById('fLocName').value.trim();

    if (!whId || !code || !name) {
        showToast('请填写必填项（仓库、编码、名称）', 'error');
        return;
    }
    const dupCode = mockData.locations.find(x => x.locationCode === code && x.id !== locId);
    if (dupCode) { showToast('仓位编码已存在，请更换', 'error'); return; }

    const w = mockData.warehouses.find(x => x.id === whId);

    if (locId) {
        const l = mockData.locations.find(x => x.id === locId);
        if (l) {
            const oldStatus = l.status;
            l.locationCode  = code;
            l.locationName  = name;
            l.locationType  = document.getElementById('fLocType').value.trim();
            l.areaCode      = document.getElementById('fLocArea').value.trim();
            l.shelfCode     = document.getElementById('fLocShelf').value.trim();
            l.levelCode     = document.getElementById('fLocLevel').value.trim();
            l.capacity      = parseInt(document.getElementById('fLocCap').value)      || l.capacity;
            l.capacityUnit  = document.getElementById('fLocCapUnit').value;
            l.materialTypes = document.getElementById('fLocMatTypes').value.trim();
            l.remark        = document.getElementById('fLocRemark').value.trim();
            const statusEl  = document.getElementById('fLocStatus');
            if (statusEl) l.status = statusEl.value;
            // 同步仓库仓位统计
            if (w && statusEl && oldStatus !== l.status) {
                if (oldStatus === 'available') { w.positionIdle = Math.max(0, w.positionIdle-1); w.positionUsed++; }
                else if (l.status === 'available') { w.positionUsed = Math.max(0, w.positionUsed-1); w.positionIdle++; }
            }
        }
    } else {
        const newLoc = {
            id: Date.now(),
            locationCode:  code,
            locationName:  name,
            warehouseId:   whId,
            warehouseName: w ? w.warehouseName : '',
            locationType:  document.getElementById('fLocType').value.trim(),
            areaCode:      document.getElementById('fLocArea').value.trim(),
            shelfCode:     document.getElementById('fLocShelf').value.trim(),
            levelCode:     document.getElementById('fLocLevel').value.trim(),
            capacity:      parseInt(document.getElementById('fLocCap').value)       || 0,
            capacityUnit:  document.getElementById('fLocCapUnit').value,
            usedCapacity:  0,
            materialTypes: document.getElementById('fLocMatTypes').value.trim(),
            status:        'available',
            remark:        document.getElementById('fLocRemark').value.trim(),
            createdAt:     new Date().toLocaleString('sv').replace('T',' ')
        };
        mockData.locations.push(newLoc);
        if (w) { w.positionTotal++; w.positionIdle++; }
    }

    closeModal();
    showToast(locId ? '编辑成功' : '新增成功');
    // 若是从仓位列表弹窗进来，回到仓位列表
    if (currentLocWarehouseId) {
        setTimeout(() => openLocationListModal(currentLocWarehouseId), 50);
    } else {
        renderWarehouseTabContent();
    }
}

/* ============================================================
   8. 仓位详情弹窗
   ============================================================ */
function showLocationDetail(locId) {
    const l = mockData.locations.find(x => x.id === locId);
    if (!l) return;
    const pct = l.capacity ? Math.round(l.usedCapacity / l.capacity * 100) : 0;

    const html = `
        <div class="modal-header">
            <div class="modal-title">仓位详情 — ${l.locationName}</div>
            <span class="modal-close" onclick="closeModal()">×</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基础信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">仓位编码</span><span class="detail-value">${l.locationCode}</span></div>
                    <div class="detail-item"><span class="detail-label">仓位名称</span><span class="detail-value">${l.locationName}</span></div>
                    <div class="detail-item"><span class="detail-label">所属仓库</span><span class="detail-value">${l.warehouseName}</span></div>
                    <div class="detail-item"><span class="detail-label">仓位类型</span><span class="detail-value">${l.locationType||'-'}</span></div>
                    <div class="detail-item"><span class="detail-label">区/货架/层</span><span class="detail-value">${l.areaCode||'-'} / ${l.shelfCode||'-'} / ${l.levelCode||'-'}</span></div>
                    <div class="detail-item"><span class="detail-label">仓位状态</span><span class="detail-value">${locStatusTag(l.status)}</span></div>
                    <div class="detail-item"><span class="detail-label">创建时间</span><span class="detail-value">${l.createdAt||'-'}</span></div>
                    <div class="detail-item"><span class="detail-label">适用物料</span><span class="detail-value">${l.materialTypes||'-'}</span></div>
                    <div class="detail-item" style="grid-column:span 2"><span class="detail-label">备注</span><span class="detail-value">${l.remark||'-'}</span></div>
                </div>
            </div>

            <div class="detail-section">
                <div class="detail-section-title">容量 & 环境要求</div>
                <div class="detail-grid">
                    <div class="detail-item">
                        <span class="detail-label">最大容量</span>
                        <span class="detail-value">${l.capacity} ${l.capacityUnit}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">已用容量</span>
                        <span class="detail-value">${l.usedCapacity} ${l.capacityUnit}（${pct}%）</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">温度范围</span>
                        <span class="detail-value">${l.temperatureMin !== null ? l.temperatureMin+'℃' : '-'} ~ ${l.temperatureMax !== null ? l.temperatureMax+'℃' : '-'}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">湿度范围</span>
                        <span class="detail-value">${l.humidityMin !== null ? l.humidityMin+'%' : '-'} ~ ${l.humidityMax !== null ? l.humidityMax+'%' : '-'}</span>
                    </div>
                </div>
                <div style="margin-top:10px">
                    <div style="font-size:13px;color:#606266;margin-bottom:6px">容量使用率：${pct}%</div>
                    <div style="background:#ebeef5;border-radius:6px;height:10px;max-width:400px">
                        <div style="width:${pct}%;background:${pct>85?'#f56c6c':pct>60?'#e6a23c':'#67c23a'};height:10px;border-radius:6px;transition:width .3s"></div>
                    </div>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
            <button class="btn btn-primary" onclick="closeModal();openLocationModal(${l.id})">编辑仓位</button>
        </div>
    `;
    showModal(html, '680px');
}

/* ============================================================
   入库单
   ============================================================ */
function renderInboundOrders(container, data) {
    const list = data || mockData.inboundOrders;
    container.innerHTML = `
        <div class="toolbar">
            <div class="toolbar-row">
                <input type="text" id="rkSearchCode" placeholder="入库单号">
                <select id="rkSearchType">
                    <option value="">全部类型</option>
                    <option value="采购入库">采购入库</option>
                    <option value="调拨入库">调拨入库</option>
                    <option value="退货入库">退货入库</option>
                    <option value="盘盈入库">盘盈入库</option>
                </select>
                <select id="rkSearchStatus">
                    <option value="">全部状态</option>
                    <option value="待审核">待审核</option>
                    <option value="已完成">已完成</option>
                </select>
                <button class="btn btn-primary" onclick="searchInboundOrders()">搜索</button>
                <button class="btn btn-default"  onclick="resetInboundSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openInboundModal()">+ 新增入库单</button>
            </div>
        </div>
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>入库单号</th><th>入库类型</th><th>供应商</th>
                        <th>入库仓库</th><th>入库数量</th><th>入库日期</th><th>状态</th><th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    ${list.map(r => `<tr>
                        <td>${r.code}</td><td>${r.type}</td><td>${r.supplier}</td>
                        <td>${r.warehouse}</td><td>${r.quantity}</td><td>${r.date}</td>
                        <td>${getStatusTag(r.status)}</td>
                        <td>
                            <div class="action-btns">
                                <button class="btn-link" onclick="showToast('详情功能开发中')">详情</button>
                                ${r.status === '待审核' ? `<button class="btn-link" onclick="openAuditModal(${r.id},'inbound')">审核</button>` : ''}
                                ${r.status === '待审核' ? `<button class="btn-link danger" onclick="deleteOrder(${r.id},'inbound')">删除</button>` : ''}
                            </div>
                        </td>
                    </tr>`).join('')}
                </tbody>
            </table>
            <div class="pagination"><span style="color:#909399;font-size:14px">共 ${list.length} 条</span></div>
        </div>
    `;
}

function searchInboundOrders() {
    const code   = document.getElementById('rkSearchCode').value.toLowerCase();
    const type   = document.getElementById('rkSearchType').value;
    const status = document.getElementById('rkSearchStatus').value;
    const filtered = mockData.inboundOrders.filter(r =>
        (!code   || r.code.toLowerCase().includes(code)) &&
        (!type   || r.type === type) &&
        (!status || r.status === status)
    );
    renderInboundOrders(document.getElementById('warehouseTabContent'), filtered);
    if (document.getElementById('rkSearchCode')) document.getElementById('rkSearchCode').value = code;
}

function resetInboundSearch() { renderInboundOrders(document.getElementById('warehouseTabContent')); }

function openInboundModal() {
    const html = `
        <div class="modal-header">
            <div class="modal-title">新增入库单</div>
            <span class="modal-close" onclick="closeModal()">×</span>
        </div>
        <div class="modal-body">
            <div class="form-grid">
                <div class="form-item">
                    <label class="form-label">入库单号</label>
                    <input class="form-input" value="RK-20260318-004" readonly>
                </div>
                <div class="form-item">
                    <label class="form-label">入库类型</label>
                    <select class="form-select">
                        <option>采购入库</option><option>调拨入库</option>
                        <option>退货入库</option><option>盘盈入库</option>
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label">供应商</label>
                    <input class="form-input" placeholder="请输入供应商">
                </div>
                <div class="form-item">
                    <label class="form-label">入库日期</label>
                    <input class="form-input" type="date" value="2026-03-18">
                </div>
            </div>
            <div style="margin-top:16px">
                <div style="font-weight:600;margin-bottom:10px">入库物料明细</div>
                <table style="border:1px solid #ebeef5">
                    <thead style="background:#f5f7fa">
                        <tr><th>物料名称</th><th>规格</th><th>仓库</th><th>仓位</th><th>数量</th><th>单位</th><th>操作</th></tr>
                    </thead>
                    <tbody id="inboundMaterialRows">
                        <tr>
                            <td><input class="form-input" style="width:90px" placeholder="物料名称"></td>
                            <td><input class="form-input" style="width:70px" placeholder="规格"></td>
                            <td><input class="form-input" style="width:80px" placeholder="仓库"></td>
                            <td><input class="form-input" style="width:60px" placeholder="仓位"></td>
                            <td><input class="form-input" type="number" style="width:60px"></td>
                            <td><input class="form-input" style="width:50px" placeholder="单位"></td>
                            <td><button class="btn-link danger" onclick="this.closest('tr').remove()">删除</button></td>
                        </tr>
                    </tbody>
                </table>
                <button class="btn btn-default add-row-btn" onclick="addInboundRow()">+ 添加物料</button>
            </div>
            <div style="margin-top:16px">
                <label class="form-label">备注</label>
                <textarea class="form-textarea" placeholder="请输入备注" style="width:100%"></textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveInboundOrder()">确认</button>
        </div>
    `;
    showModal(html, '800px');
}

function addInboundRow() {
    const tbody = document.getElementById('inboundMaterialRows');
    const tr = document.createElement('tr');
    tr.innerHTML = `
        <td><input class="form-input" style="width:90px" placeholder="物料名称"></td>
        <td><input class="form-input" style="width:70px" placeholder="规格"></td>
        <td><input class="form-input" style="width:80px" placeholder="仓库"></td>
        <td><input class="form-input" style="width:60px" placeholder="仓位"></td>
        <td><input class="form-input" type="number" style="width:60px"></td>
        <td><input class="form-input" style="width:50px" placeholder="单位"></td>
        <td><button class="btn-link danger" onclick="this.closest('tr').remove()">删除</button></td>
    `;
    tbody.appendChild(tr);
}

function saveInboundOrder() {
    mockData.inboundOrders.unshift({
        id: Date.now(), code: 'RK-20260318-004', type: '采购入库',
        supplier: '新供应商', warehouse: '主食材冷藏库',
        quantity: '0kg', date: '2026-03-18', status: '待审核'
    });
    closeModal();
    renderWarehouseTabContent();
    showToast('入库单创建成功');
}

/* ============================================================
   出库单
   ============================================================ */
function renderOutboundOrders(container, data) {
    const list = data || mockData.outboundOrders;
    container.innerHTML = `
        <div class="toolbar">
            <div class="toolbar-row">
                <input type="text" id="ckSearchCode" placeholder="出库单号">
                <select id="ckSearchType">
                    <option value="">全部类型</option>
                    <option>领用出库</option><option>调拨出库</option><option>报废出库</option>
                </select>
                <select id="ckSearchStatus">
                    <option value="">全部状态</option>
                    <option>待审核</option><option>已完成</option>
                </select>
                <button class="btn btn-primary" onclick="searchOutboundOrders()">搜索</button>
                <button class="btn btn-default"  onclick="resetOutboundSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="showToast('新增出库单功能开发中')">+ 新增出库单</button>
            </div>
        </div>
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>出库单号</th><th>出库类型</th><th>领用部门</th>
                        <th>出库仓库</th><th>出库数量</th><th>出库日期</th><th>状态</th><th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    ${list.map(r => `<tr>
                        <td>${r.code}</td><td>${r.type}</td><td>${r.dept}</td>
                        <td>${r.warehouse}</td><td>${r.quantity}</td><td>${r.date}</td>
                        <td>${getStatusTag(r.status)}</td>
                        <td>
                            <div class="action-btns">
                                <button class="btn-link" onclick="showToast('详情功能开发中')">详情</button>
                                ${r.status === '待审核' ? `<button class="btn-link" onclick="openAuditModal(${r.id},'outbound')">审核</button>` : ''}
                                ${r.status === '待审核' ? `<button class="btn-link danger" onclick="deleteOrder(${r.id},'outbound')">删除</button>` : ''}
                            </div>
                        </td>
                    </tr>`).join('')}
                </tbody>
            </table>
            <div class="pagination"><span style="color:#909399;font-size:14px">共 ${list.length} 条</span></div>
        </div>
    `;
}

function searchOutboundOrders() {
    const code   = document.getElementById('ckSearchCode').value.toLowerCase();
    const type   = document.getElementById('ckSearchType').value;
    const status = document.getElementById('ckSearchStatus').value;
    const filtered = mockData.outboundOrders.filter(r =>
        (!code   || r.code.toLowerCase().includes(code)) &&
        (!type   || r.type === type) &&
        (!status || r.status === status)
    );
    renderOutboundOrders(document.getElementById('warehouseTabContent'), filtered);
}

function resetOutboundSearch() { renderOutboundOrders(document.getElementById('warehouseTabContent')); }

/* ============================================================
   盘点单
   ============================================================ */
function renderStocktakeOrders(container, data) {
    const list = data || mockData.stocktakeOrders;
    container.innerHTML = `
        <div class="toolbar">
            <div class="toolbar-row">
                <input type="text" id="pdSearchCode" placeholder="盘点单号">
                <select id="pdSearchWarehouse">
                    <option value="">全部仓库</option>
                    ${mockData.warehouses.map(w => `<option value="${w.warehouseName}">${w.warehouseName}</option>`).join('')}
                </select>
                <select id="pdSearchStatus">
                    <option value="">全部状态</option>
                    <option>待完成</option><option>待审核</option><option>已完成</option>
                </select>
                <button class="btn btn-primary" onclick="searchStocktake()">搜索</button>
                <button class="btn btn-default"  onclick="resetStocktakeSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="showToast('新增盘点单功能开发中')">+ 新增盘点单</button>
            </div>
        </div>
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>盘点单号</th><th>盘点仓库</th><th>盘点日期</th><th>盘点人</th>
                        <th>盘点物料数</th><th>差异数量</th><th>状态</th><th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    ${list.map(r => `<tr>
                        <td>${r.code}</td><td>${r.warehouse}</td><td>${r.date}</td><td>${r.person}</td>
                        <td>${r.materialCount}</td><td>${r.diff}</td>
                        <td>${getStatusTag(r.status)}</td>
                        <td>
                            <div class="action-btns">
                                <button class="btn-link" onclick="showToast('详情功能开发中')">详情</button>
                                ${r.status === '待审核' ? `<button class="btn-link" onclick="openAuditModal(${r.id},'stocktake')">审核</button>` : ''}
                            </div>
                        </td>
                    </tr>`).join('')}
                </tbody>
            </table>
            <div class="pagination"><span style="color:#909399;font-size:14px">共 ${list.length} 条</span></div>
        </div>
    `;
}

function searchStocktake() {
    const code      = document.getElementById('pdSearchCode').value.toLowerCase();
    const warehouse = document.getElementById('pdSearchWarehouse').value;
    const status    = document.getElementById('pdSearchStatus').value;
    const filtered  = mockData.stocktakeOrders.filter(r =>
        (!code      || r.code.toLowerCase().includes(code)) &&
        (!warehouse || r.warehouse === warehouse) &&
        (!status    || r.status === status)
    );
    renderStocktakeOrders(document.getElementById('warehouseTabContent'), filtered);
}

function resetStocktakeSearch() { renderStocktakeOrders(document.getElementById('warehouseTabContent')); }

/* ============================================================
   通用：单据删除 & 审核弹窗
   ============================================================ */
function deleteOrder(id, type) {
    if (confirm('确定要删除该单据吗？')) {
        const arr = type === 'inbound' ? mockData.inboundOrders
                  : type === 'outbound' ? mockData.outboundOrders
                  : mockData.stocktakeOrders;
        const idx = arr.findIndex(x => x.id === id);
        if (idx !== -1) arr.splice(idx, 1);
        renderWarehouseTabContent();
        showToast('删除成功');
    }
}

function openAuditModal(id, type) {
    const html = `
        <div class="modal-header">
            <div class="modal-title">审核</div>
            <span class="modal-close" onclick="closeModal()">×</span>
        </div>
        <div class="modal-body">
            <div class="form-item">
                <label class="form-label">审核意见</label>
                <textarea class="form-textarea" id="auditComment"
                    placeholder="请输入审核意见（可选）" style="min-height:100px"></textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-default" style="color:#f56c6c;border-color:#f56c6c"
                    onclick="auditOrder(${id},'${type}','reject')">驳回</button>
            <button class="btn btn-primary" onclick="auditOrder(${id},'${type}','pass')">通过</button>
        </div>
    `;
    showModal(html, '500px');
}

function auditOrder(id, type, result) {
    const arr  = type === 'inbound' ? mockData.inboundOrders
               : type === 'outbound' ? mockData.outboundOrders
               : mockData.stocktakeOrders;
    const item = arr.find(x => x.id === id);
    if (item) item.status = result === 'pass' ? '已完成' : '已驳回';
    closeModal();
    renderWarehouseTabContent();
    showToast(result === 'pass' ? '审核通过' : '已驳回');
}
