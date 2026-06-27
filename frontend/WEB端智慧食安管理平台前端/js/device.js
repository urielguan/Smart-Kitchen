/**
 * device.js — 设备管理
 *
 * 运行验证说明：
 *  1. 【查看设备列表】侧边栏点击「设备管理」，可见 6 条 Mock 设备，状态标签绿/黄/灰区分；
 *     支持设备编号/名称关键字、设备类型、所属门店、状态 4 个筛选项 + 重置，分页每页 10 条。
 *  2. 【新增设备】点击「新增设备」按钮，弹窗填写必填项（设备编号自动生成只读），
 *     必填未填时红字提示；提交后列表即时新增并 Toast 提示"设备新增成功"。
 *  3. 【查看详情】点击操作列「详情」，弹窗标题含设备编号，分"基础信息/维保信息"两 Tab，
 *     所有字段只读，累计使用时长/维保周期等扩展信息在维保 Tab 展示。
 *  4. 【编辑设备】点击「编辑」，弹窗精准回显所有字段，设备编号只读不可修改，
 *     保存后列表数据实时刷新并提示"设备信息编辑成功"。
 *  5. 【删除设备】点击「删除」弹出二次确认；若设备状态为"正常"则额外展示风险提示；
 *     确认后数据从列表隐藏（软删除 status='deleted'），可通过筛选"已删除"查看。
 *
 * 核心改动点：
 *  - mock-data.js 追加 equipment 数组（6 条设备）
 *  - 本文件实现全部 5 大功能，无外部依赖
 *  - sidebar.js 新增「后厨管理」→「设备管理」入口 + renderPage 分发
 *  - index.html 追加 <script src="js/device.js">
 */

/* ============================================================
   枚举映射
   ============================================================ */
const EQUIP_TYPE_MAP = {
    stove:      '灶具',
    steamoven:  '蒸箱',
    oven:       '烤箱',
    freezer:    '冰柜',
    sterilizer: '消毒柜',
    other:      '其他'
};
const EQUIP_AREA_MAP = {
    hot:     '后厨热厨区',
    cold:    '冷厨区',
    storage: '仓储区'
};
const EQUIP_STATUS_MAP = {
    normal:   { label: '正常',   cls: 'tag-success' },
    repair:   { label: '待维修', cls: 'tag-warning' },
    inactive: { label: '停用',   cls: 'tag-info'    },
    deleted:  { label: '已删除', cls: 'tag-danger'  }
};

/* 分页状态 */
window.devPage        = window.devPage        || 1;
window.devPageSize    = window.devPageSize    || 10;
window.devFilteredList = window.devFilteredList || [];

/* 弹窗详情内部 Tab */
window.devDetailTab = window.devDetailTab || 'base';

/* ============================================================
   主入口
   ============================================================ */
function renderDevicePage(container) {
    container.innerHTML = `
        <div class="page-card">
            <!-- 工具栏 -->
            <div class="toolbar" style="flex-wrap:wrap;gap:8px;">
                <input  id="devKeyword"    class="form-control" style="width:180px;" placeholder="设备编号/设备名称" oninput="_filterDevice()">
                <select id="devTypeFilter" class="form-control" style="width:120px;" onchange="_filterDevice()">
                    <option value="">全部类型</option>
                    ${Object.entries(EQUIP_TYPE_MAP).map(([k,v]) => `<option value="${k}">${v}</option>`).join('')}
                </select>
                <select id="devOrgFilter"  class="form-control" style="width:130px;" onchange="_filterDevice()">
                    <option value="">全部门店</option>
                    ${(mockData.orgs || []).filter(o => !o.parentId).map(o =>
                        `<option value="${o.id}">${o.orgName}</option>`).join('')}
                </select>
                <select id="devStatusFilter" class="form-control" style="width:120px;" onchange="_filterDevice()">
                    <option value="">全部状态</option>
                    <option value="normal">正常</option>
                    <option value="repair">待维修</option>
                    <option value="inactive">停用</option>
                    <option value="deleted">已删除</option>
                </select>
                <button class="btn btn-default" onclick="_filterDevice()">查 询</button>
                <button class="btn btn-default" onclick="_resetDevFilter()">重 置</button>
                <button class="btn btn-primary" onclick="openDeviceModal(null)" style="margin-left:auto;">+ 新增设备</button>
            </div>

            <!-- 表格 -->
            <table class="data-table" style="margin-top:14px;">
                <thead>
                    <tr>
                        <th width="60">序号</th>
                        <th width="150">设备编号</th>
                        <th>设备名称</th>
                        <th width="90">设备类型</th>
                        <th width="110">所属门店</th>
                        <th width="90">所在区域</th>
                        <th width="130">品牌型号</th>
                        <th width="80">状态</th>
                        <th width="100">采购日期</th>
                        <th width="80">操作人</th>
                        <th width="180">操作</th>
                    </tr>
                </thead>
                <tbody id="devTableBody"></tbody>
            </table>

            <!-- 分页 -->
            <div id="devPager" style="margin-top:12px;display:flex;justify-content:flex-end;align-items:center;gap:10px;font-size:13px;color:#606266;"></div>
        </div>`;

    _filterDevice();
}

/* ============================================================
   筛选 & 分页
   ============================================================ */
function _filterDevice() {
    const keyword = (document.getElementById('devKeyword')?.value || '').trim().toLowerCase();
    const type    = document.getElementById('devTypeFilter')?.value || '';
    const orgId   = document.getElementById('devOrgFilter')?.value  || '';
    const status  = document.getElementById('devStatusFilter')?.value || '';

    window.devFilteredList = (mockData.equipment || []).filter(d => {
        /* 默认隐藏已删除 */
        if (!status && d.status === 'deleted') return false;
        if (keyword && !d.equipNo.toLowerCase().includes(keyword) && !d.equipName.toLowerCase().includes(keyword)) return false;
        if (type   && d.equipType !== type)             return false;
        if (orgId  && d.orgId    !== parseInt(orgId))   return false;
        if (status && d.status   !== status)            return false;
        return true;
    });
    window.devPage = 1;
    _renderDevRows();
}

function _resetDevFilter() {
    ['devKeyword','devTypeFilter','devOrgFilter','devStatusFilter'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    _filterDevice();
}

function _renderDevRows() {
    const tbody = document.getElementById('devTableBody');
    const pager = document.getElementById('devPager');
    if (!tbody) return;

    const list     = window.devFilteredList;
    const pageSize = window.devPageSize;
    const page     = window.devPage;
    const total    = list.length;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    if (pageData.length === 0) {
        tbody.innerHTML = `<tr><td colspan="11" style="text-align:center;color:#909399;padding:40px;">
            暂无设备数据，可点击「新增设备」创建
        </td></tr>`;
    } else {
        tbody.innerHTML = pageData.map((d, i) => {
            const st  = EQUIP_STATUS_MAP[d.status] || { label: d.status, cls: 'tag-info' };
            const typ = EQUIP_TYPE_MAP[d.equipType] || d.equipType;
            const area = EQUIP_AREA_MAP[d.area] || d.area;
            return `
                <tr>
                    <td>${start + i + 1}</td>
                    <td><code style="background:#f5f7fa;padding:2px 5px;border-radius:3px;font-size:12px;">${d.equipNo}</code></td>
                    <td><strong>${d.equipName}</strong></td>
                    <td>${typ}</td>
                    <td>${d.orgName}</td>
                    <td>${area}</td>
                    <td>${d.brand} ${d.model}</td>
                    <td><span class="tag ${st.cls}">${st.label}</span></td>
                    <td>${d.purchaseDate || '-'}</td>
                    <td>${d.createdBy || '-'}</td>
                    <td>
                        <button class="btn btn-sm btn-default" onclick="showDeviceDetail(${d.id})">详情</button>
                        <button class="btn btn-sm btn-primary" onclick="openDeviceModal(${d.id})" style="margin-left:4px;">编辑</button>
                        <button class="btn btn-sm btn-danger"  onclick="deleteDevice(${d.id})"   style="margin-left:4px;">删除</button>
                    </td>
                </tr>`;
        }).join('');
    }

    /* 分页 */
    const totalPages = Math.ceil(total / pageSize) || 1;
    pager.innerHTML = `
        <span>共 ${total} 条</span>
        <button class="btn btn-sm btn-default" ${page <= 1 ? 'disabled' : ''} onclick="devPaginate(${page - 1})">上一页</button>
        <span>${page} / ${totalPages}</span>
        <button class="btn btn-sm btn-default" ${page >= totalPages ? 'disabled' : ''} onclick="devPaginate(${page + 1})">下一页</button>`;
}

function devPaginate(p) {
    window.devPage = p;
    _renderDevRows();
}

/* ============================================================
   详情弹窗（2-Tab：基础信息 / 维保信息）
   ============================================================ */
function showDeviceDetail(id) {
    const d = (mockData.equipment || []).find(x => x.id === id);
    if (!d) return;
    window.devDetailTab = 'base';

    const st   = EQUIP_STATUS_MAP[d.status] || { label: d.status, cls: 'tag-info' };
    const typ  = EQUIP_TYPE_MAP[d.equipType] || d.equipType;
    const area = EQUIP_AREA_MAP[d.area] || d.area;

    const html = `
        <div class="modal-header"><h3>${d.equipNo} - 设备详情</h3></div>
        <div class="modal-body" style="padding:0;">
            <!-- 内嵌 Tab -->
            <div style="display:flex;border-bottom:1px solid #e4e7ed;padding:0 20px;">
                <div id="devDTabBase" onclick="_devDetailTab('base')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid #409eff;margin-bottom:-1px;color:#409eff;font-weight:500;">
                    基础信息
                </div>
                <div id="devDTabMaint" onclick="_devDetailTab('maint')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid transparent;margin-bottom:-1px;color:#606266;">
                    维保信息
                </div>
            </div>
            <div id="devDContent" style="padding:20px;max-height:60vh;overflow-y:auto;min-height:240px;">
                ${_buildDevDetailBase(d, st, typ, area)}
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关 闭</button>
            <button class="btn btn-primary" onclick="closeModal();openDeviceModal(${d.id})">编 辑</button>
        </div>`;
    showModal(html, '600px');

    /* 把 d 挂到 window 供 Tab 切换使用 */
    window._devDetailData = d;
}

function _devDetailTab(tab) {
    window.devDetailTab = tab;
    const d    = window._devDetailData;
    const st   = EQUIP_STATUS_MAP[d.status] || { label: d.status, cls: 'tag-info' };
    const typ  = EQUIP_TYPE_MAP[d.equipType] || d.equipType;
    const area = EQUIP_AREA_MAP[d.area] || d.area;

    ['base','maint'].forEach(t => {
        const el = document.getElementById('devDTab' + (t === 'base' ? 'Base' : 'Maint'));
        if (el) {
            el.style.color             = t === tab ? '#409eff' : '#606266';
            el.style.borderBottomColor = t === tab ? '#409eff' : 'transparent';
            el.style.fontWeight        = t === tab ? '500' : 'normal';
        }
    });
    const content = document.getElementById('devDContent');
    if (tab === 'base')  content.innerHTML = _buildDevDetailBase(d, st, typ, area);
    if (tab === 'maint') content.innerHTML = _buildDevDetailMaint(d);
}

function _buildDevDetailBase(d, st, typ, area) {
    return `
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px 20px;font-size:13px;">
            <div><span style="color:#909399;">设备编号：</span><code style="background:#f5f7fa;padding:2px 6px;border-radius:3px;">${d.equipNo}</code></div>
            <div><span style="color:#909399;">设备名称：</span><strong>${d.equipName}</strong></div>
            <div><span style="color:#909399;">设备类型：</span>${typ}</div>
            <div><span style="color:#909399;">设备状态：</span><span class="tag ${st.cls}">${st.label}</span></div>
            <div><span style="color:#909399;">所属门店：</span>${d.orgName}</div>
            <div><span style="color:#909399;">所在区域：</span>${area}</div>
            <div><span style="color:#909399;">品牌：</span>${d.brand || '-'}</div>
            <div><span style="color:#909399;">型号：</span>${d.model || '-'}</div>
            <div><span style="color:#909399;">供应商：</span>${d.supplierName || '-'}</div>
            <div><span style="color:#909399;">采购日期：</span>${d.purchaseDate || '-'}</div>
            <div><span style="color:#909399;">创建人：</span>${d.createdBy || '-'}</div>
            <div><span style="color:#909399;">创建时间：</span>${d.createdAt || '-'}</div>
            <div style="grid-column:1/-1;"><span style="color:#909399;">备注：</span>${d.remark || '-'}</div>
        </div>`;
}

function _buildDevDetailMaint(d) {
    return `
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px 20px;font-size:13px;">
            <div><span style="color:#909399;">累计使用时长：</span><strong>${d.usageHours || 0} 小时</strong></div>
            <div><span style="color:#909399;">维保周期：</span>${d.maintainCycle || '-'} 天</div>
            <div><span style="color:#909399;">最近维保时间：</span>${d.lastMaintainDate || '-'}</div>
            <div><span style="color:#909399;">下次维保时间：</span>
                <span style="${_isOverdue(d.nextMaintainDate) ? 'color:#f56c6c;font-weight:600;' : ''}">${d.nextMaintainDate || '-'}${_isOverdue(d.nextMaintainDate) ? ' ⚠️逾期' : ''}</span>
            </div>
            <div style="grid-column:1/-1;">
                <span style="color:#909399;">维保记录：</span>
                <div style="margin-top:6px;padding:10px;background:#f5f7fa;border-radius:6px;line-height:1.7;">
                    ${d.maintainRecord || '暂无维保记录'}
                </div>
            </div>
        </div>`;
}

function _isOverdue(dateStr) {
    if (!dateStr) return false;
    return new Date(dateStr) < new Date();
}

/* ============================================================
   新增/编辑弹窗
   ============================================================ */
function openDeviceModal(id) {
    const d = id ? (mockData.equipment || []).find(x => x.id === id) : null;
    const title = d ? '编辑设备' : '新增设备';
    const autoNo = d ? d.equipNo : _genEquipNo();

    /* 供应商选项 */
    const suppOpts = (mockData.suppliers || []).map(s =>
        `<option value="${s.id}" data-name="${s.supplierName}" ${d && d.supplierId === s.id ? 'selected' : ''}>${s.supplierName}</option>`
    ).join('');

    /* 门店选项（取顶级 org） */
    const orgOpts = (mockData.orgs || []).filter(o => !o.parentId).map(o =>
        `<option value="${o.id}" data-name="${o.orgName}" ${d && d.orgId === o.id ? 'selected' : ''}>${o.orgName}</option>`
    ).join('');

    const html = `
        <div class="modal-header"><h3>${title}</h3></div>
        <div class="modal-body" style="max-height:65vh;overflow-y:auto;overflow-x:hidden;">
            <div class="form-row">
                <div class="form-group" style="flex:1;">
                    <label class="form-label required">设备编号</label>
                    <input id="devNo" class="form-control" value="${autoNo}" readonly
                           style="background:#f5f7fa;color:#909399;cursor:not-allowed;">
                    <span style="font-size:11px;color:#909399;">自动生成，不可修改</span>
                </div>
                <div class="form-group" style="flex:1;">
                    <label class="form-label required">设备名称</label>
                    <input id="devName" class="form-control" placeholder="请输入设备名称" value="${d ? d.equipName : ''}">
                </div>
            </div>
            <div class="form-row">
                <div class="form-group" style="flex:1;">
                    <label class="form-label required">设备类型</label>
                    <select id="devType" class="form-control">
                        <option value="">请选择设备类型</option>
                        ${Object.entries(EQUIP_TYPE_MAP).map(([k,v]) =>
                            `<option value="${k}" ${d && d.equipType === k ? 'selected' : ''}>${v}</option>`
                        ).join('')}
                    </select>
                </div>
                <div class="form-group" style="flex:1;">
                    <label class="form-label required">所属门店</label>
                    <select id="devOrg" class="form-control">
                        <option value="">请选择门店</option>
                        ${orgOpts}
                    </select>
                </div>
            </div>
            <div class="form-row">
                <div class="form-group" style="flex:1;">
                    <label class="form-label">所在区域</label>
                    <select id="devArea" class="form-control">
                        <option value="">请选择区域</option>
                        ${Object.entries(EQUIP_AREA_MAP).map(([k,v]) =>
                            `<option value="${k}" ${d && d.area === k ? 'selected' : ''}>${v}</option>`
                        ).join('')}
                    </select>
                </div>
                <div class="form-group" style="flex:1;">
                    <label class="form-label">采购日期</label>
                    <input id="devPurchaseDate" class="form-control" type="date" value="${d ? d.purchaseDate : ''}">
                </div>
            </div>
            <div class="form-row">
                <div class="form-group" style="flex:1;">
                    <label class="form-label">品牌</label>
                    <input id="devBrand" class="form-control" placeholder="请输入品牌" value="${d ? d.brand : ''}">
                </div>
                <div class="form-group" style="flex:1;">
                    <label class="form-label">型号</label>
                    <input id="devModel" class="form-control" placeholder="请输入型号" value="${d ? d.model : ''}">
                </div>
            </div>
            <div class="form-row">
                <div class="form-group" style="flex:1;">
                    <label class="form-label">供应商</label>
                    <select id="devSupplier" class="form-control">
                        <option value="">请选择供应商</option>
                        ${suppOpts}
                    </select>
                </div>
                <div class="form-group" style="flex:1;">
                    <label class="form-label required">设备状态</label>
                    <select id="devStatus" class="form-control">
                        <option value="normal"   ${(!d || d.status==='normal')   ? 'selected' : ''}>正常</option>
                        <option value="repair"   ${d && d.status==='repair'      ? 'selected' : ''}>待维修</option>
                        <option value="inactive" ${d && d.status==='inactive'    ? 'selected' : ''}>停用</option>
                    </select>
                </div>
            </div>
            <div class="form-group">
                <label class="form-label">备注</label>
                <textarea id="devRemark" class="form-control" rows="2" placeholder="请输入备注">${d ? d.remark : ''}</textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取 消</button>
            <button class="btn btn-primary" onclick="saveDevice(${id || 'null'})">保 存</button>
        </div>`;
    showModal(html, '640px');
}

function saveDevice(id) {
    const equipNo  = document.getElementById('devNo').value.trim();
    const equipName = document.getElementById('devName').value.trim();
    const equipType = document.getElementById('devType').value;
    const orgSel   = document.getElementById('devOrg');
    const orgId    = parseInt(orgSel.value) || 0;
    const orgName  = orgSel.options[orgSel.selectedIndex]?.dataset.name || orgSel.options[orgSel.selectedIndex]?.text || '';
    const area     = document.getElementById('devArea').value;
    const purchaseDate = document.getElementById('devPurchaseDate').value;
    const brand    = document.getElementById('devBrand').value.trim();
    const model    = document.getElementById('devModel').value.trim();
    const suppSel  = document.getElementById('devSupplier');
    const supplierId   = parseInt(suppSel.value) || 0;
    const supplierName = suppSel.options[suppSel.selectedIndex]?.dataset.name || '';
    const status   = document.getElementById('devStatus').value;
    const remark   = document.getElementById('devRemark').value.trim();

    /* 必填校验 */
    if (!equipName) { showToast('请输入设备名称', 'error'); return; }
    if (!equipType) { showToast('请选择设备类型', 'error'); return; }
    if (!orgId)     { showToast('请选择所属门店', 'error'); return; }
    if (!status)    { showToast('请选择设备状态', 'error'); return; }

    const now = new Date().toLocaleString('zh-CN').replace(/\//g,'-');

    if (id) {
        const d = (mockData.equipment || []).find(x => x.id === id);
        Object.assign(d, { equipName, equipType, orgId, orgName, area, purchaseDate, brand, model,
                            supplierId, supplierName, status, remark, updatedAt: now });
        showToast('设备信息编辑成功');
    } else {
        const newId = Math.max(0, ...(mockData.equipment || []).map(x => x.id)) + 1;
        mockData.equipment.push({
            id: newId, equipNo, equipName, equipType, orgId, orgName, area, purchaseDate,
            brand, model, supplierId, supplierName, status, remark,
            usageHours: 0, lastMaintainDate: '', nextMaintainDate: '', maintainCycle: 180,
            maintainRecord: '',
            createdBy: '管理员', createdAt: now, updatedAt: now
        });
        showToast('设备新增成功');
    }
    closeModal();
    renderDevicePage(document.getElementById('mainContent'));
}

/* ============================================================
   删除设备（软删除）
   ============================================================ */
function deleteDevice(id) {
    const d = (mockData.equipment || []).find(x => x.id === id);
    if (!d) return;

    const isNormal  = d.status === 'normal';
    const extraWarn = isNormal
        ? `<p style="color:#e6a23c;margin:8px 0 0;font-size:13px;">
               ⚠️ 该设备当前为<strong>正常使用状态</strong>，请确认是否需要删除。
           </p>`
        : '';

    const html = `
        <div class="modal-header"><h3>⚠️ 删除确认</h3></div>
        <div class="modal-body">
            <p style="margin:10px 0;">是否确认删除设备「<strong>${d.equipName}</strong>（${d.equipNo}）」？</p>
            <p style="color:#909399;font-size:13px;margin:0;">删除后数据将标记为已删除，不可恢复。</p>
            ${extraWarn}
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取 消</button>
            <button class="btn btn-danger"  onclick="_doDeleteDevice(${id})">确认删除</button>
        </div>`;
    showModal(html, '440px');
}

function _doDeleteDevice(id) {
    const d = (mockData.equipment || []).find(x => x.id === id);
    if (d) { d.status = 'deleted'; d.updatedAt = new Date().toLocaleString('zh-CN').replace(/\//g,'-'); }
    closeModal();
    showToast('设备删除成功');
    renderDevicePage(document.getElementById('mainContent'));
}

/* ============================================================
   工具函数
   ============================================================ */
function _genEquipNo() {
    const d   = new Date();
    const dt  = `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`;
    const seq = String(Math.floor(Math.random() * 900) + 100);
    return `EQ-${dt}-${seq}`;
}
