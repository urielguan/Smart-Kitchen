/**
 * employee.js — 员工管理
 *
 * 运行验证说明：
 *  1. 【查看员工列表】侧边栏 系统管理 → 员工管理，可见 6 条 Mock 员工；
 *     状态标签绿(启用)/灰(禁用)；支持姓名/编号关键字 + 所属门店 + 所属部门 + 账号状态筛选+重置，分页每页10条。
 *  2. 【新增员工】点击「新增员工」→ 弹窗分「基础信息」/「权限分配」两 Tab；
 *     员工编号自动生成只读；手机号 11 位格式校验，不符合提示"请输入11位有效手机号"；
 *     权限分配 Tab 勾选角色后实时展示该角色功能权限概览；
 *     保存后 Toast 提示"员工新增成功，权限已同步分配"，列表即时新增。
 *  3. 【查看员工详情】点击「详情」→ 弹窗标题含员工编号 → 分「基础信息」/「权限信息」/「入职信息」3 Tab 只读展示；
 *     权限信息 Tab 展示已分配角色及功能/数据权限概览。
 *  4. 【编辑员工】点击「编辑」→ 弹窗精准回显，员工编号只读；手机号仍格式校验；
 *     若修改了账号状态，保存后额外 Toast 提示"账号状态已更新，生效需重新登录"；
 *     保存后 Toast 提示"员工信息及权限编辑成功"，列表实时刷新。
 *
 * 核心改动点：
 *  - mock-data.js 追加 employees 数组（6 条员工）
 *  - 本文件实现全部 4 大功能，与 roles/orgs Mock 数据联动
 *  - sidebar.js 「系统管理」组 employee 入口绑定 renderEmployeePage 分发
 *  - index.html 追加 <script src="js/employee.js">
 */

/* ============================================================
   枚举映射
   ============================================================ */
const EMP_POSITION_MAP = {
    chef:        '厨师',
    cookworker:  '厨工',
    manager:     '店长',
    purchaser:   '采购员'
};
const EMP_STATUS_MAP = {
    active:   { label: '启用', cls: 'tag-success' },
    inactive: { label: '禁用', cls: 'tag-info'    }
};
const EMP_GENDER_MAP = { male: '男', female: '女' };

/* 分页 */
window.empPage         = window.empPage         || 1;
window.empPageSize     = window.empPageSize     || 10;
window.empFilteredList = window.empFilteredList || [];

/* 弹窗内部 Tab */
window.empModalTab  = window.empModalTab  || 'base';
window.empDetailTab = window.empDetailTab || 'base';

/* ============================================================
   主入口
   ============================================================ */
function renderEmployeePage(container) {
    /* 构建门店选项（顶级 + 二级以上 canteen/company） */
    const orgOpts = (mockData.orgs || []).filter(o => o.orgType !== 'dept').map(o =>
        `<option value="${o.id}">${o.orgName}</option>`).join('');

    /* 构建部门选项（orgType === 'dept'） */
    const deptOpts = (mockData.orgs || []).filter(o => o.orgType === 'dept').map(o =>
        `<option value="${o.id}">${o.orgName}</option>`).join('');

    container.innerHTML = `
        <div class="page-card">
            <!-- 工具栏 -->
            <div class="toolbar" style="flex-wrap:wrap;gap:8px;">
                <input  id="empKeyword"    class="form-control" style="width:190px;" placeholder="员工编号/姓名/手机号" oninput="_filterEmployee()">
                <select id="empOrgFilter"  class="form-control" style="width:130px;" onchange="_filterEmployee()">
                    <option value="">全部门店</option>
                    ${orgOpts}
                </select>
                <select id="empDeptFilter" class="form-control" style="width:130px;" onchange="_filterEmployee()">
                    <option value="">全部部门</option>
                    ${deptOpts}
                </select>
                <select id="empStatusFilter" class="form-control" style="width:110px;" onchange="_filterEmployee()">
                    <option value="">全部状态</option>
                    <option value="active">启用</option>
                    <option value="inactive">禁用</option>
                </select>
                <button class="btn btn-default" onclick="_filterEmployee()">查 询</button>
                <button class="btn btn-default" onclick="_resetEmpFilter()">重 置</button>
                <button class="btn btn-primary" onclick="openEmpModal(null)" style="margin-left:auto;">+ 新增员工</button>
            </div>

            <!-- 表格 -->
            <table class="data-table" style="margin-top:14px;">
                <thead>
                    <tr>
                        <th width="55">序号</th>
                        <th width="150">员工编号</th>
                        <th width="80">姓名</th>
                        <th width="120">手机号</th>
                        <th width="120">所属门店</th>
                        <th width="120">所属部门</th>
                        <th width="80">职位</th>
                        <th>所属角色</th>
                        <th width="80">状态</th>
                        <th width="100">入职日期</th>
                        <th width="170">操作</th>
                    </tr>
                </thead>
                <tbody id="empTableBody"></tbody>
            </table>

            <!-- 分页 -->
            <div id="empPager" style="margin-top:12px;display:flex;justify-content:flex-end;align-items:center;gap:10px;font-size:13px;color:#606266;"></div>
        </div>`;

    _filterEmployee();
}

/* ============================================================
   筛选 & 分页
   ============================================================ */
function _filterEmployee() {
    const keyword = (document.getElementById('empKeyword')?.value || '').trim().toLowerCase();
    const orgId   = document.getElementById('empOrgFilter')?.value  || '';
    const deptId  = document.getElementById('empDeptFilter')?.value || '';
    const status  = document.getElementById('empStatusFilter')?.value || '';

    window.empFilteredList = (mockData.employees || []).filter(e => {
        if (keyword && !e.empNo.toLowerCase().includes(keyword) &&
                       !e.name.toLowerCase().includes(keyword)  &&
                       !e.phone.includes(keyword)) return false;
        if (orgId  && e.orgId  !== parseInt(orgId))  return false;
        if (deptId && e.deptId !== parseInt(deptId)) return false;
        if (status && e.status !== status)           return false;
        return true;
    });
    window.empPage = 1;
    _renderEmpRows();
}

function _resetEmpFilter() {
    ['empKeyword','empOrgFilter','empDeptFilter','empStatusFilter'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    _filterEmployee();
}

function _renderEmpRows() {
    const tbody = document.getElementById('empTableBody');
    const pager = document.getElementById('empPager');
    if (!tbody) return;

    const list     = window.empFilteredList;
    const pageSize = window.empPageSize;
    const page     = window.empPage;
    const total    = list.length;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    if (pageData.length === 0) {
        tbody.innerHTML = `<tr><td colspan="11" style="text-align:center;color:#909399;padding:40px;">
            暂无员工数据，可点击「新增员工」创建
        </td></tr>`;
    } else {
        tbody.innerHTML = pageData.map((e, i) => {
            const st  = EMP_STATUS_MAP[e.status] || { label: e.status, cls: 'tag-info' };
            const pos = EMP_POSITION_MAP[e.position] || e.position;
            return `
                <tr>
                    <td>${start + i + 1}</td>
                    <td><code style="background:#f5f7fa;padding:2px 5px;border-radius:3px;font-size:12px;">${e.empNo}</code></td>
                    <td><strong>${e.name}</strong></td>
                    <td>${e.phone}</td>
                    <td>${e.orgName}</td>
                    <td>${e.deptName || '-'}</td>
                    <td>${pos}</td>
                    <td style="font-size:12px;color:#606266;">${e.roleNames || '-'}</td>
                    <td><span class="tag ${st.cls}">${st.label}</span></td>
                    <td>${e.hireDate || '-'}</td>
                    <td>
                        <button class="btn btn-sm btn-default" onclick="showEmpDetail(${e.id})">详情</button>
                        <button class="btn btn-sm btn-primary" onclick="openEmpModal(${e.id})" style="margin-left:4px;">编辑</button>
                    </td>
                </tr>`;
        }).join('');
    }

    const totalPages = Math.ceil(total / pageSize) || 1;
    pager.innerHTML = `
        <span>共 ${total} 条</span>
        <button class="btn btn-sm btn-default" ${page <= 1 ? 'disabled' : ''} onclick="empPaginate(${page - 1})">上一页</button>
        <span>${page} / ${totalPages}</span>
        <button class="btn btn-sm btn-default" ${page >= totalPages ? 'disabled' : ''} onclick="empPaginate(${page + 1})">下一页</button>`;
}

function empPaginate(p) {
    window.empPage = p;
    _renderEmpRows();
}

/* ============================================================
   员工详情（3-Tab 只读）
   ============================================================ */
function showEmpDetail(id) {
    const e = (mockData.employees || []).find(x => x.id === id);
    if (!e) return;
    window.empDetailTab = 'base';
    window._empDetailData = e;

    const html = `
        <div class="modal-header"><h3>${e.empNo} - 员工详情</h3></div>
        <div class="modal-body" style="padding:0;">
            <div style="display:flex;border-bottom:1px solid #e4e7ed;padding:0 20px;">
                <div id="empDTabBase" onclick="_empDetailTab('base')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid #409eff;margin-bottom:-1px;color:#409eff;font-weight:500;">
                    基础信息
                </div>
                <div id="empDTabPerm" onclick="_empDetailTab('perm')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid transparent;margin-bottom:-1px;color:#606266;">
                    权限信息
                </div>
                <div id="empDTabHire" onclick="_empDetailTab('hire')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid transparent;margin-bottom:-1px;color:#606266;">
                    入职信息
                </div>
            </div>
            <div id="empDContent" style="padding:20px;max-height:60vh;overflow-y:auto;min-height:220px;">
                ${_buildEmpDetailBase(e)}
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关 闭</button>
            <button class="btn btn-primary" onclick="closeModal();openEmpModal(${e.id})">编 辑</button>
        </div>`;
    showModal(html, '600px');
}

function _empDetailTab(tab) {
    window.empDetailTab = tab;
    const e = window._empDetailData;
    ['base','perm','hire'].forEach(t => {
        const labelMap = { base:'Base', perm:'Perm', hire:'Hire' };
        const el = document.getElementById('empDTab' + labelMap[t]);
        if (el) {
            el.style.color             = t === tab ? '#409eff' : '#606266';
            el.style.borderBottomColor = t === tab ? '#409eff' : 'transparent';
            el.style.fontWeight        = t === tab ? '500' : 'normal';
        }
    });
    const content = document.getElementById('empDContent');
    if (tab === 'base') content.innerHTML = _buildEmpDetailBase(e);
    if (tab === 'perm') content.innerHTML = _buildEmpDetailPerm(e);
    if (tab === 'hire') content.innerHTML = _buildEmpDetailHire(e);
}

function _buildEmpDetailBase(e) {
    const st  = EMP_STATUS_MAP[e.status] || { label: e.status, cls: 'tag-info' };
    const pos = EMP_POSITION_MAP[e.position] || e.position;
    return `
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px 20px;font-size:13px;">
            <div><span style="color:#909399;">员工编号：</span><code style="background:#f5f7fa;padding:2px 6px;border-radius:3px;">${e.empNo}</code></div>
            <div><span style="color:#909399;">姓名：</span><strong>${e.name}</strong></div>
            <div><span style="color:#909399;">性别：</span>${EMP_GENDER_MAP[e.gender] || '-'}</div>
            <div><span style="color:#909399;">手机号：</span>${e.phone}</div>
            <div><span style="color:#909399;">邮箱：</span>${e.email || '-'}</div>
            <div><span style="color:#909399;">身份证号：</span>${e.idCard ? e.idCard.replace(/(.{6}).+(.{4})/, '$1********$2') : '-'}</div>
            <div><span style="color:#909399;">所属门店：</span>${e.orgName}</div>
            <div><span style="color:#909399;">所属部门：</span>${e.deptName || '-'}</div>
            <div><span style="color:#909399;">职位：</span>${pos}</div>
            <div><span style="color:#909399;">账号状态：</span><span class="tag ${st.cls}">${st.label}</span></div>
        </div>`;
}

function _buildEmpDetailPerm(e) {
    if (!e.roleIds || e.roleIds.length === 0) {
        return '<div style="color:#909399;text-align:center;padding:30px;">暂未分配角色</div>';
    }
    let html = '';
    e.roleIds.forEach(rid => {
        const role = (mockData.roles || []).find(r => r.id === rid);
        if (!role) return;
        const scopeLabel = { all: '全部数据', org: '本机构数据', dept: '本部门数据' }[role.dataScope] || '-';
        const funcLabels = _getRoleFuncLabels(role.funcPermissions || []);
        html += `
            <div style="margin-bottom:16px;padding:12px;background:#f5f7fa;border-radius:6px;">
                <div style="display:flex;align-items:center;gap:10px;margin-bottom:8px;">
                    <strong style="font-size:14px;">${role.roleName}</strong>
                    <code style="background:#e4e7ed;padding:1px 6px;border-radius:3px;font-size:11px;">${role.roleCode}</code>
                    <span class="tag ${role.status==='active' ? 'tag-success':'tag-info'}" style="font-size:11px;">${role.status==='active'?'启用':'禁用'}</span>
                </div>
                <div style="font-size:12px;color:#606266;line-height:1.8;">
                    <span style="color:#909399;">数据权限：</span>${scopeLabel}<br>
                    <span style="color:#909399;">功能模块（${role.funcPermissions.length}个）：</span>
                    <span>${funcLabels || '暂无'}</span>
                </div>
            </div>`;
    });
    return html;
}

function _buildEmpDetailHire(e) {
    return `
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px 20px;font-size:13px;">
            <div><span style="color:#909399;">入职日期：</span>${e.hireDate || '-'}</div>
            <div><span style="color:#909399;">创建人：</span>${e.createdBy || '-'}</div>
            <div><span style="color:#909399;">创建时间：</span>${e.createdAt || '-'}</div>
            <div><span style="color:#909399;">最后更新：</span>${e.updatedAt || '-'}</div>
            <div style="grid-column:1/-1;"><span style="color:#909399;">备注：</span>${e.remark || '-'}</div>
        </div>`;
}

/* 将功能权限 key 转为可读标签 */
function _getRoleFuncLabels(keys) {
    const labelMap = {
        dashboard:'数据看板', supplier:'供应商管理', purchasePlan:'采购计划', purchase:'采购订单',
        warehouse:'仓库管理', material:'物料管理', inventory:'库存汇总',
        inbound:'入库管理', outbound:'出库管理', stocktake:'盘点管理',
        recipe:'菜谱库', plan:'菜谱计划', cook:'烹饪记录', sample:'留样管理',
        org:'组织管理', employee:'员工管理', rolePermission:'角色权限管理'
    };
    return keys.map(k => labelMap[k] || k).join('、');
}

/* ============================================================
   新增/编辑弹窗（2-Tab：基础信息 / 权限分配）
   ============================================================ */
function openEmpModal(id) {
    window.empModalTab = 'base';
    window._empEditId  = id;
    const e = id ? (mockData.employees || []).find(x => x.id === id) : null;

    const html = `
        <div class="modal-header"><h3>${e ? '编辑员工' : '新增员工'}</h3></div>
        <div class="modal-body" style="padding:0;">
            <!-- 内嵌 Tab 导航 -->
            <div style="display:flex;border-bottom:1px solid #e4e7ed;padding:0 20px;">
                <div id="empMTabBase" onclick="empModalSwitchTab('base')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid #409eff;margin-bottom:-1px;color:#409eff;font-weight:500;">
                    基础信息
                </div>
                <div id="empMTabPerm" onclick="empModalSwitchTab('perm')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid transparent;margin-bottom:-1px;color:#606266;">
                    权限分配
                </div>
            </div>
            <!-- Tab 内容 -->
            <div id="empMContent" style="padding:20px;min-height:300px;max-height:60vh;overflow-y:auto;overflow-x:hidden;">
                ${_buildEmpBaseTab(e)}
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取 消</button>
            <button class="btn btn-primary" onclick="saveEmployee(${id || 'null'})">保 存</button>
        </div>`;
    showModal(html, '660px');
}

function empModalSwitchTab(tab) {
    window.empModalTab = tab;
    ['base','perm'].forEach(t => {
        const el = document.getElementById('empMTab' + (t === 'base' ? 'Base' : 'Perm'));
        if (el) {
            el.style.color             = t === tab ? '#409eff' : '#606266';
            el.style.borderBottomColor = t === tab ? '#409eff' : 'transparent';
            el.style.fontWeight        = t === tab ? '500' : 'normal';
        }
    });
    const id = window._empEditId;
    const e  = id ? (mockData.employees || []).find(x => x.id === id) : null;
    const content = document.getElementById('empMContent');
    if (tab === 'base') content.innerHTML = _buildEmpBaseTab(e);
    if (tab === 'perm') content.innerHTML = _buildEmpPermTab(e);
}

/* ---- 基础信息 Tab ---- */
function _buildEmpBaseTab(e) {
    const autoNo = e ? e.empNo : _genEmpNo();

    /* 门店（非部门） */
    const orgOpts = (mockData.orgs || []).filter(o => o.orgType !== 'dept').map(o =>
        `<option value="${o.id}" data-name="${o.orgName}" ${e && e.orgId === o.id ? 'selected' : ''}>${o.orgName}</option>`
    ).join('');

    /* 部门 */
    const deptOpts = (mockData.orgs || []).filter(o => o.orgType === 'dept').map(o =>
        `<option value="${o.id}" data-name="${o.orgName}" ${e && e.deptId === o.id ? 'selected' : ''}>${o.orgName}</option>`
    ).join('');

    return `
        <div class="form-row">
            <div class="form-group" style="flex:1;">
                <label class="form-label required">员工编号</label>
                <input id="empNo" class="form-control" value="${autoNo}" readonly
                       style="background:#f5f7fa;color:#909399;cursor:not-allowed;">
                <span style="font-size:11px;color:#909399;">自动生成，不可修改</span>
            </div>
            <div class="form-group" style="flex:1;">
                <label class="form-label required">姓名</label>
                <input id="empName" class="form-control" placeholder="请输入姓名" value="${e ? e.name : ''}">
            </div>
        </div>
        <div class="form-row">
            <div class="form-group" style="flex:1;">
                <label class="form-label">性别</label>
                <div style="display:flex;gap:20px;margin-top:8px;">
                    <label style="display:flex;align-items:center;gap:6px;cursor:pointer;">
                        <input type="radio" name="empGender" value="male"   ${(!e || e.gender==='male')   ? 'checked' : ''}> 男
                    </label>
                    <label style="display:flex;align-items:center;gap:6px;cursor:pointer;">
                        <input type="radio" name="empGender" value="female" ${e && e.gender==='female'    ? 'checked' : ''}> 女
                    </label>
                </div>
            </div>
            <div class="form-group" style="flex:1;">
                <label class="form-label required">手机号</label>
                <input id="empPhone" class="form-control" placeholder="请输入11位手机号" maxlength="11"
                       value="${e ? e.phone : ''}" oninput="_validateEmpPhone()">
                <span id="empPhoneTip" style="font-size:11px;color:#f56c6c;display:none;">请输入11位有效手机号</span>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group" style="flex:1;">
                <label class="form-label required">所属门店</label>
                <select id="empOrg" class="form-control">
                    <option value="">请选择门店</option>
                    ${orgOpts}
                </select>
            </div>
            <div class="form-group" style="flex:1;">
                <label class="form-label">所属部门</label>
                <select id="empDept" class="form-control">
                    <option value="">请选择部门（可选）</option>
                    ${deptOpts}
                </select>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group" style="flex:1;">
                <label class="form-label">职位</label>
                <select id="empPosition" class="form-control">
                    <option value="">请选择职位</option>
                    ${Object.entries(EMP_POSITION_MAP).map(([k,v]) =>
                        `<option value="${k}" ${e && e.position === k ? 'selected' : ''}>${v}</option>`
                    ).join('')}
                </select>
            </div>
            <div class="form-group" style="flex:1;">
                <label class="form-label">入职日期</label>
                <input id="empHireDate" class="form-control" type="date" value="${e ? e.hireDate : ''}">
            </div>
        </div>
        <div class="form-row">
            <div class="form-group" style="flex:1;">
                <label class="form-label required">账号状态</label>
                <select id="empStatus" class="form-control">
                    <option value="active"   ${(!e || e.status==='active')   ? 'selected' : ''}>启用</option>
                    <option value="inactive" ${e && e.status==='inactive'    ? 'selected' : ''}>禁用</option>
                </select>
            </div>
            <div class="form-group" style="flex:1;">
                <label class="form-label">邮箱</label>
                <input id="empEmail" class="form-control" placeholder="请输入邮箱（可选）" value="${e ? e.email : ''}">
            </div>
        </div>
        <div class="form-row">
            <div class="form-group" style="flex:1;">
                <label class="form-label">身份证号</label>
                <input id="empIdCard" class="form-control" placeholder="请输入身份证号（可选）" value="${e ? e.idCard : ''}">
            </div>
        </div>
        <div class="form-group">
            <label class="form-label">备注</label>
            <textarea id="empRemark" class="form-control" rows="2" placeholder="请输入备注">${e ? e.remark : ''}</textarea>
        </div>`;
}

function _validateEmpPhone() {
    const val = document.getElementById('empPhone')?.value || '';
    const tip = document.getElementById('empPhoneTip');
    if (!tip) return;
    tip.style.display = (val.length > 0 && !/^1[3-9]\d{9}$/.test(val)) ? 'inline' : 'none';
}

/* ---- 权限分配 Tab ---- */
function _buildEmpPermTab(e) {
    const assignedIds = new Set(e ? (e.roleIds || []) : []);
    const roles = mockData.roles || [];

    let rolesHtml = '';
    roles.forEach(r => {
        const st = r.status === 'active' ? '<span class="tag tag-success" style="font-size:11px;">启用</span>'
                                         : '<span class="tag tag-info"    style="font-size:11px;">禁用</span>';
        const funcPreview = _getRoleFuncLabels((r.funcPermissions || []).slice(0, 4));
        const more = (r.funcPermissions || []).length > 4 ? `等 ${r.funcPermissions.length} 个模块` : '';
        rolesHtml += `
            <div style="border:1px solid #e4e7ed;border-radius:6px;padding:10px 12px;margin-bottom:8px;">
                <div style="display:flex;align-items:flex-start;gap:8px;">
                    <input type="checkbox" id="empRole_${r.id}" value="${r.id}"
                           ${assignedIds.has(r.id) ? 'checked' : ''}
                           onchange="_onEmpRoleChange()"
                           style="margin-top:3px;cursor:pointer;">
                    <div style="flex:1;">
                        <label for="empRole_${r.id}" style="cursor:pointer;font-weight:500;">${r.roleName}</label>
                        <code style="background:#f0f2f5;padding:1px 5px;border-radius:3px;font-size:11px;margin-left:6px;">${r.roleCode}</code>
                        ${st}
                        <div id="empRolePerm_${r.id}" style="margin-top:6px;font-size:12px;color:#909399;display:${assignedIds.has(r.id) ? 'block':'none'};">
                            功能模块：${funcPreview}${more ? '、'+more : ''}；
                            数据范围：${{ all:'全部', org:'本机构', dept:'本部门' }[r.dataScope] || '-'}
                        </div>
                    </div>
                </div>
            </div>`;
    });

    return `
        <div style="margin-bottom:10px;font-size:13px;color:#606266;">
            勾选角色后，员工将继承该角色的功能权限与数据权限：
        </div>
        ${rolesHtml || '<div style="color:#909399;text-align:center;padding:20px;">暂无可分配角色，请先在「角色权限管理」中创建角色</div>'}`;
}

function _onEmpRoleChange() {
    /* 勾选/取消时实时展示/隐藏权限概览 */
    (mockData.roles || []).forEach(r => {
        const chk = document.getElementById(`empRole_${r.id}`);
        const perm = document.getElementById(`empRolePerm_${r.id}`);
        if (chk && perm) perm.style.display = chk.checked ? 'block' : 'none';
    });
}

/* ============================================================
   保存员工
   ============================================================ */
function saveEmployee(id) {
    /* 读取基础信息 */
    const empNoEl    = document.getElementById('empNo');
    const nameEl     = document.getElementById('empName');
    const phoneEl    = document.getElementById('empPhone');
    const orgEl      = document.getElementById('empOrg');
    const deptEl     = document.getElementById('empDept');
    const posEl      = document.getElementById('empPosition');
    const hireDateEl = document.getElementById('empHireDate');
    const statusEl   = document.getElementById('empStatus');
    const emailEl    = document.getElementById('empEmail');
    const idCardEl   = document.getElementById('empIdCard');
    const remarkEl   = document.getElementById('empRemark');

    /* 若当前停留在权限 Tab，须获取基础字段（先切回，用存储值） */
    if (!nameEl) {
        showToast('请先完善基础信息 Tab 的内容', 'error'); return;
    }

    const empNo   = empNoEl ? empNoEl.value.trim() : '';
    const name    = nameEl.value.trim();
    const phone   = phoneEl ? phoneEl.value.trim() : '';
    const orgId   = parseInt(orgEl?.value || 0);
    const orgName = orgEl?.options[orgEl.selectedIndex]?.dataset.name || orgEl?.options[orgEl.selectedIndex]?.text || '';
    const deptId  = parseInt(deptEl?.value || 0);
    const deptName = deptEl && deptEl.value
        ? (deptEl.options[deptEl.selectedIndex]?.dataset.name || deptEl.options[deptEl.selectedIndex]?.text || '-')
        : '-';
    const position  = posEl?.value || '';
    const hireDate  = hireDateEl?.value || '';
    const status    = statusEl?.value || 'active';
    const email     = emailEl?.value.trim() || '';
    const idCard    = idCardEl?.value.trim() || '';
    const remark    = remarkEl?.value.trim() || '';
    const genderRadio = document.querySelector('input[name="empGender"]:checked');
    const gender    = genderRadio ? genderRadio.value : 'male';

    /* 必填校验 */
    if (!name)  { showToast('请输入姓名', 'error'); return; }
    if (!phone) { showToast('请输入手机号', 'error'); return; }
    if (!/^1[3-9]\d{9}$/.test(phone)) { showToast('请输入11位有效手机号', 'error'); return; }
    if (!orgId) { showToast('请选择所属门店', 'error'); return; }

    /* 读取权限 Tab 已勾选角色（若渲染过） */
    const roleChks = document.querySelectorAll('input[id^="empRole_"]:checked');
    let roleIds, roleNames;
    if (roleChks.length > 0 || document.querySelector('input[id^="empRole_"]')) {
        roleIds   = Array.from(document.querySelectorAll('input[id^="empRole_"]'))
                         .filter(c => c.checked).map(c => parseInt(c.value));
        roleNames = roleIds.map(rid => {
            const r = (mockData.roles || []).find(x => x.id === rid);
            return r ? r.roleName : '';
        }).filter(Boolean).join('、');
    } else {
        /* 权限 Tab 未渲染，保留原值 */
        const orig = id ? (mockData.employees || []).find(x => x.id === id) : null;
        roleIds   = orig ? (orig.roleIds || [])   : [];
        roleNames = orig ? (orig.roleNames || '') : '';
    }

    const now = new Date().toLocaleString('zh-CN').replace(/\//g, '-');

    /* 判断账号状态是否发生变更 */
    let statusChanged = false;
    if (id) {
        const orig = (mockData.employees || []).find(x => x.id === id);
        statusChanged = orig && orig.status !== status;
    }

    if (id) {
        const emp = (mockData.employees || []).find(x => x.id === id);
        Object.assign(emp, {
            name, gender, phone, email, idCard,
            orgId, orgName, deptId, deptName,
            position, hireDate, status, remark,
            roleIds, roleNames, updatedAt: now
        });
        showToast('员工信息及权限编辑成功');
        if (statusChanged) {
            setTimeout(() => showToast('账号状态已更新，生效需重新登录', 'error'), 1500);
        }
    } else {
        const newId = Math.max(0, ...(mockData.employees || []).map(x => x.id)) + 1;
        mockData.employees.push({
            id: newId, empNo, name, gender, phone, email, idCard,
            orgId, orgName, deptId, deptName,
            position, hireDate, status, remark,
            roleIds, roleNames,
            createdBy: '管理员', createdAt: now, updatedAt: now
        });
        showToast('员工新增成功，权限已同步分配');
    }

    closeModal();
    renderEmployeePage(document.getElementById('mainContent'));
}

/* ============================================================
   工具
   ============================================================ */
function _genEmpNo() {
    const d  = new Date();
    const dt = `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`;
    const seq = String(Math.floor(Math.random() * 900) + 100);
    return `EMP-${dt}-${seq}`;
}
