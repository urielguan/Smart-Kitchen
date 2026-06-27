/**
 * rolePermission.js — 角色权限管理
 *
 * 功能：
 *  - Tab1 角色分组管理：新增、编辑、删除分组
 *  - Tab2 角色管理：新增、编辑、删除、查看列表、查看详情
 *    新增/编辑角色：3-Tab 表单（基础信息 / 功能权限 / 数据权限）
 */

/* ============================================================
   功能权限模块配置
   ============================================================ */
const FUNC_MODULE_CONFIG = [
    {
        module: '数据概览',
        key: 'dashboard',
        actions: ['查看']
    },
    {
        module: '采购管理',
        children: [
            { key: 'supplier',     label: '供应商管理', actions: ['查看', '新增', '编辑', '删除'] },
            { key: 'purchasePlan', label: '采购计划',   actions: ['查看', '新增', '编辑', '审核', '删除'] },
            { key: 'purchase',     label: '采购订单',   actions: ['查看', '新增', '编辑', '审核', '删除'] }
        ]
    },
    {
        module: '仓储管理',
        children: [
            { key: 'warehouse',  label: '仓库信息管理', actions: ['查看', '新增', '编辑', '删除'] },
            { key: 'material',   label: '物料信息管理', actions: ['查看', '新增', '编辑', '删除'] },
            { key: 'inventory',  label: '库存汇总',     actions: ['查看'] },
            { key: 'inbound',    label: '入库管理',     actions: ['查看', '新增', '编辑', '审核', '删除'] },
            { key: 'outbound',   label: '出库管理',     actions: ['查看', '新增', '编辑', '审核', '删除'] },
            { key: 'stocktake',  label: '盘点管理',     actions: ['查看', '新增', '编辑', '审核', '删除'] }
        ]
    },
    {
        module: '菜谱营养',
        children: [
            { key: 'recipe', label: '菜谱库管理', actions: ['查看', '新增', '编辑', '删除'] },
            { key: 'plan',   label: '菜谱计划',   actions: ['查看', '新增', '编辑', '审核', '删除'] }
        ]
    },
    {
        module: '后厨管理',
        children: [
            { key: 'cook',   label: '烹饪记录', actions: ['查看', '新增'] },
            { key: 'sample', label: '留样管理', actions: ['查看', '新增', '编辑', '删除'] }
        ]
    },
    {
        module: '系统管理',
        children: [
            { key: 'org',            label: '组织管理',   actions: ['查看', '新增', '编辑', '删除'] },
            { key: 'employee',       label: '员工管理',   actions: ['查看', '新增', '编辑', '删除'] },
            { key: 'rolePermission', label: '角色权限管理', actions: ['查看', '新增', '编辑', '删除'] }
        ]
    }
];

/* 角色弹窗内部 Tab：'base' | 'func' | 'data' */
window.rpModalTab = window.rpModalTab || 'base';

/* 分组折叠状态：groupId -> boolean (true=展开) */
window.rpGroupExpanded = window.rpGroupExpanded || {};

/* 搜索关键字缓存 */
window.rpSearchKeyword = window.rpSearchKeyword || '';

/* ============================================================
   主入口 — 折叠面板布局
   ============================================================ */
function renderRolePermissionPage(container) {
    container.innerHTML = `
        <div class="page-card">
            <!-- 顶部操作栏 -->
            <div style="display:flex;align-items:center;gap:10px;margin-bottom:20px;flex-wrap:wrap;">
                <!-- 左侧：搜索 + 筛选 -->
                <div style="position:relative;flex:1;min-width:220px;max-width:320px;">
                    <span style="position:absolute;left:10px;top:50%;transform:translateY(-50%);color:#909399;font-size:14px;">🔍</span>
                    <input id="rpSearchInput" class="form-control"
                           style="padding-left:32px;"
                           placeholder="搜索角色名称或编码"
                           value="${window.rpSearchKeyword}"
                           oninput="_rpOnSearch(this.value)">
                </div>
                <button class="btn btn-default" onclick="_rpOnSearch(document.getElementById('rpSearchInput').value)"
                        style="display:flex;align-items:center;gap:5px;">
                    <span>▼</span> 筛选
                </button>
                <!-- 右侧：操作按钮 -->
                <div style="margin-left:auto;display:flex;gap:8px;">
                    <button class="btn btn-default" onclick="openRpGroupModal(null)"
                            style="display:flex;align-items:center;gap:5px;">
                        <span>📁</span> 新增分组
                    </button>
                    <button class="btn btn-primary" onclick="openRoleModal(null)"
                            style="display:flex;align-items:center;gap:5px;">
                        <span>＋</span> 新增角色
                    </button>
                </div>
            </div>
            <!-- 折叠面板列表 -->
            <div id="rpAccordionList"></div>
        </div>`;

    _rpRenderAccordion();
}

/* ============================================================
   折叠面板渲染
   ============================================================ */
function _rpRenderAccordion() {
    const list = document.getElementById('rpAccordionList');
    if (!list) return;

    const keyword = (window.rpSearchKeyword || '').trim().toLowerCase();
    const groups  = mockData.roleGroups;

    if (groups.length === 0) {
        list.innerHTML = `<div style="text-align:center;color:#909399;padding:50px;">
            暂无角色分组，点击「新增分组」创建
        </div>`;
        return;
    }

    let html = '';
    groups.forEach(g => {
        /* 当前分组下所有角色（含关键字筛选） */
        let roles = mockData.roles.filter(r => r.groupId === g.id);
        if (keyword) {
            roles = roles.filter(r =>
                r.roleName.toLowerCase().includes(keyword) ||
                r.roleCode.toLowerCase().includes(keyword)
            );
        }

        /* 分组展开/折叠状态：默认展开 */
        if (window.rpGroupExpanded[g.id] === undefined) {
            window.rpGroupExpanded[g.id] = true;
        }
        const expanded = window.rpGroupExpanded[g.id];

        /* 角色卡片 */
        const cardsHtml = roles.map(r => _buildRoleCard(r)).join('');

        html += `
            <div style="margin-bottom:12px;border:1px solid #e4e7ed;border-radius:8px;overflow:hidden;">
                <!-- 分组标题栏 -->
                <div onclick="_rpToggleGroup(${g.id})"
                     style="background:#ecf5ff;padding:14px 18px;cursor:pointer;
                            display:flex;align-items:flex-start;justify-content:space-between;
                            border-bottom:${expanded ? '1px solid #d9ecff' : 'none'};
                            transition:background 0.15s;"
                     onmouseover="this.style.background='#d9ecff'"
                     onmouseout="this.style.background='#ecf5ff'">
                    <div style="flex:1;">
                        <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px;">
                            <span style="font-size:16px;">📂</span>
                            <strong style="font-size:15px;color:#303133;">${g.groupName}</strong>
                            <span style="font-size:13px;color:#409eff;background:#fff;
                                         border:1px solid #d9ecff;border-radius:12px;
                                         padding:1px 10px;">${roles.length} 个角色</span>
                        </div>
                        ${g.remark ? `<div style="font-size:12px;color:#909399;margin-left:24px;">${g.remark}</div>` : ''}
                    </div>
                    <!-- 右侧：操作 + 箭头 -->
                    <div style="display:flex;align-items:center;gap:8px;margin-left:16px;">
                        <button class="btn btn-sm btn-default"
                                onclick="event.stopPropagation();openRpGroupModal(${g.id})"
                                style="font-size:12px;">编辑分组</button>
                        <button class="btn btn-sm btn-danger"
                                onclick="event.stopPropagation();deleteRpGroup(${g.id})"
                                style="font-size:12px;">删除分组</button>
                        <span style="font-size:12px;color:#409eff;transition:transform 0.2s;
                                     display:inline-block;transform:rotate(${expanded ? '0' : '-90'}deg);">▼</span>
                    </div>
                </div>
                <!-- 角色卡片区 -->
                <div id="rpGroup_${g.id}" style="display:${expanded ? 'block' : 'none'};
                          background:#fff;padding:${roles.length ? '12px' : '0'};">
                    ${roles.length
                        ? `<div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px;">${cardsHtml}</div>`
                        : `<div style="text-align:center;color:#c0c4cc;padding:24px;font-size:13px;">
                               该分组暂无角色，<span style="color:#409eff;cursor:pointer;" onclick="openRoleModal(null)">+ 新增角色</span>
                           </div>`
                    }
                </div>
            </div>`;
    });

    /* 搜索无结果时补充提示 */
    if (keyword) {
        const totalMatch = groups.reduce((acc, g) => {
            return acc + mockData.roles.filter(r =>
                r.groupId === g.id &&
                (r.roleName.toLowerCase().includes(keyword) || r.roleCode.toLowerCase().includes(keyword))
            ).length;
        }, 0);
        if (totalMatch === 0) {
            html = `<div style="text-align:center;color:#909399;padding:50px;">
                未找到与「${keyword}」匹配的角色
            </div>`;
        }
    }

    list.innerHTML = html;
}

/* 单个角色卡片 */
function _buildRoleCard(r) {
    const stTag = r.status === 'active'
        ? '<span class="tag tag-success" style="font-size:11px;">启用</span>'
        : '<span class="tag tag-info"    style="font-size:11px;">禁用</span>';

    /* 关联该角色的员工数 */
    const memberCount = (mockData.employees || []).filter(e =>
        (e.roleIds || []).includes(r.id)
    ).length;

    const scopeLabel = { all: '全部数据', org: '本机构', dept: '本部门' }[r.dataScope] || '-';
    const funcCount  = (r.funcPermissions || []).length;

    return `
        <div style="border:1px solid #e4e7ed;border-radius:6px;padding:14px 16px;background:#fff;
                    transition:box-shadow 0.2s;"
             onmouseover="this.style.boxShadow='0 2px 12px rgba(0,0,0,0.08)'"
             onmouseout="this.style.boxShadow='none'">
            <!-- 卡片顶部：图标 + 名称 + 操作 -->
            <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:10px;">
                <div style="display:flex;align-items:center;gap:8px;flex:1;min-width:0;">
                    <span style="font-size:18px;flex-shrink:0;">🔑</span>
                    <div style="min-width:0;">
                        <div style="font-weight:600;font-size:14px;color:#303133;
                                    overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
                            ${r.roleName}
                        </div>
                    </div>
                    ${stTag}
                </div>
                <!-- 操作按钮组 -->
                <div style="display:flex;gap:6px;flex-shrink:0;margin-left:8px;">
                    <!-- 绿色：关联员工 -->
                    <button title="查看关联员工"
                            onclick="rpViewRoleMembers(${r.id})"
                            style="width:28px;height:28px;border:none;border-radius:4px;cursor:pointer;
                                   background:#f0f9eb;color:#67c23a;font-size:14px;
                                   display:flex;align-items:center;justify-content:center;">👥</button>
                    <!-- 黄色：编辑 -->
                    <button title="编辑角色"
                            onclick="openRoleModal(${r.id})"
                            style="width:28px;height:28px;border:none;border-radius:4px;cursor:pointer;
                                   background:#fdf6ec;color:#e6a23c;font-size:14px;
                                   display:flex;align-items:center;justify-content:center;">✏️</button>
                    <!-- 红色：删除 -->
                    <button title="删除角色"
                            onclick="deleteRole(${r.id})"
                            style="width:28px;height:28px;border:none;border-radius:4px;cursor:pointer;
                                   background:#fef0f0;color:#f56c6c;font-size:14px;
                                   display:flex;align-items:center;justify-content:center;">🗑️</button>
                </div>
            </div>
            <!-- 卡片信息行 -->
            <div style="font-size:12px;color:#909399;line-height:1.9;">
                <div>编码：<code style="background:#f5f7fa;padding:1px 5px;border-radius:3px;color:#606266;">${r.roleCode}</code></div>
                <div style="display:flex;align-items:center;gap:4px;">
                    <span>👤 ${memberCount} 人</span>
                    <span style="color:#ddd;">|</span>
                    <span>📋 ${funcCount} 个功能模块</span>
                    <span style="color:#ddd;">|</span>
                    <span>🗂️ ${scopeLabel}</span>
                </div>
                ${r.remark ? `<div style="color:#c0c4cc;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${r.remark}</div>` : ''}
            </div>
            <!-- 详情入口 -->
            <div style="margin-top:10px;border-top:1px solid #f5f7fa;padding-top:8px;text-align:right;">
                <span onclick="showRoleDetail(${r.id})"
                      style="font-size:12px;color:#409eff;cursor:pointer;">查看详情 →</span>
            </div>
        </div>`;
}

/* 展开/折叠分组 */
function _rpToggleGroup(groupId) {
    window.rpGroupExpanded[groupId] = !window.rpGroupExpanded[groupId];
    const panel = document.getElementById(`rpGroup_${groupId}`);
    if (panel) {
        panel.style.display = window.rpGroupExpanded[groupId] ? 'block' : 'none';
    }
    /* 更新箭头 — 重新渲染比较简单，代价可接受 */
    _rpRenderAccordion();
}

/* 搜索 */
function _rpOnSearch(val) {
    window.rpSearchKeyword = val;
    _rpRenderAccordion();
}

/* 查看角色关联员工 */
function rpViewRoleMembers(roleId) {
    const r = mockData.roles.find(x => x.id === roleId);
    if (!r) return;
    const members = (mockData.employees || []).filter(e => (e.roleIds || []).includes(roleId));
    let rows = members.length
        ? members.map((e, i) => `
            <tr>
                <td>${i + 1}</td>
                <td>${e.empNo}</td>
                <td>${e.name}</td>
                <td>${e.orgName}</td>
                <td>${e.deptName || '-'}</td>
                <td>${e.status === 'active'
                    ? '<span class="tag tag-success">启用</span>'
                    : '<span class="tag tag-info">禁用</span>'}</td>
            </tr>`).join('')
        : '<tr><td colspan="6" style="text-align:center;color:#909399;padding:20px;">该角色暂无关联员工</td></tr>';

    const html = `
        <div class="modal-header"><h3>「${r.roleName}」关联员工（${members.length} 人）</h3></div>
        <div class="modal-body" style="max-height:65vh;overflow-y:auto;">
            <table class="data-table">
                <thead><tr><th>序号</th><th>员工编号</th><th>姓名</th><th>所属门店</th><th>所属部门</th><th>状态</th></tr></thead>
                <tbody>${rows}</tbody>
            </table>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关 闭</button>
        </div>`;
    showModal(html, '580px');
}

/* ============================================================
   分组相关弹窗（新增/编辑/删除）— 逻辑不变，刷新改为折叠面板
   ============================================================ */
function rpRenderGroupTab(container) {
    /* 兼容旧调用路径，直接渲染折叠面板 */
    renderRolePermissionPage(document.getElementById('mainContent'));
}

/* 打开分组新增/编辑弹窗 */
function openRpGroupModal(id) {
    const g = id ? mockData.roleGroups.find(x => x.id === id) : null;
    const title = g ? '编辑角色分组' : '新增角色分组';
    const html = `
        <div class="modal-header"><h3>${title}</h3></div>
        <div class="modal-body">
            <div class="form-row">
                <div class="form-group" style="flex:1;">
                    <label class="form-label required">分组名称</label>
                    <input id="rpgName" class="form-control" placeholder="请输入分组名称" value="${g ? g.groupName : ''}">
                </div>
                <div class="form-group" style="width:100px;">
                    <label class="form-label">排序</label>
                    <input id="rpgSort" class="form-control" type="number" min="1" value="${g ? g.sort : (mockData.roleGroups.length + 1)}">
                </div>
            </div>
            <div class="form-group">
                <label class="form-label">备注</label>
                <textarea id="rpgRemark" class="form-control" rows="2" placeholder="请输入备注">${g ? g.remark : ''}</textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取 消</button>
            <button class="btn btn-primary" onclick="saveRpGroup(${id || 'null'})">保 存</button>
        </div>`;
    showModal(html, '480px');
}

function saveRpGroup(id) {
    const name   = document.getElementById('rpgName').value.trim();
    const sort   = parseInt(document.getElementById('rpgSort').value) || 1;
    const remark = document.getElementById('rpgRemark').value.trim();
    if (!name) { showToast('请输入分组名称', 'error'); return; }

    if (id) {
        const g = mockData.roleGroups.find(x => x.id === id);
        g.groupName = name; g.sort = sort; g.remark = remark;
        showToast('分组编辑成功');
    } else {
        const newId = Math.max(0, ...mockData.roleGroups.map(x => x.id)) + 1;
        mockData.roleGroups.push({
            id: newId, groupName: name, sort, remark,
            createdAt: new Date().toLocaleString('zh-CN').replace(/\//g, '-')
        });
        showToast('分组新增成功');
    }
    closeModal();
    renderRolePermissionPage(document.getElementById('mainContent'));
}

function deleteRpGroup(id) {
    const roleCount = mockData.roles.filter(r => r.groupId === id).length;
    if (roleCount > 0) {
        showToast(`该分组下有 ${roleCount} 个角色，请先移除或删除角色后再操作`, 'error');
        return;
    }
    const g = mockData.roleGroups.find(x => x.id === id);
    const html = `
        <div class="modal-header"><h3>⚠️ 删除确认</h3></div>
        <div class="modal-body">
            <p style="margin:10px 0;">确定要删除角色分组「<strong>${g.groupName}</strong>」吗？此操作不可恢复。</p>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取 消</button>
            <button class="btn btn-danger"  onclick="_doDeleteRpGroup(${id})">确认删除</button>
        </div>`;
    showModal(html, '420px');
}

function _doDeleteRpGroup(id) {
    mockData.roleGroups = mockData.roleGroups.filter(x => x.id !== id);
    closeModal();
    showToast('分组已删除');
    renderRolePermissionPage(document.getElementById('mainContent'));
}

/* ============================================================
   角色列表（折叠面板架构下不再需要独立 Tab，保留空函数兼容旧引用）
   ============================================================ */
function rpRenderRoleTab() { renderRolePermissionPage(document.getElementById('mainContent')); }
function _filterRpRole() {}
function _resetRpRoleFilter() {}
function _renderRpRoleRows() {}
function rpRolePaginate() {}

const DATA_SCOPE_MAP = { all: '全部数据', org: '本机构数据', dept: '本部门数据' };

/* ============================================================
   角色详情
   ============================================================ */
function showRoleDetail(id) {
    const r = mockData.roles.find(x => x.id === id);
    if (!r) return;

    /* 统计已授权模块数 */
    const permCount = r.funcPermissions ? r.funcPermissions.length : 0;

    /* 功能权限展示 */
    let funcHtml = '';
    FUNC_MODULE_CONFIG.forEach(m => {
        if (m.children) {
            m.children.forEach(c => {
                const granted = r.funcPermissions && r.funcPermissions.includes(c.key);
                funcHtml += `
                    <div style="display:flex;align-items:center;padding:4px 0;border-bottom:1px solid #f0f0f0;">
                        <span style="width:140px;color:#606266;font-size:13px;">${m.module} · ${c.label}</span>
                        <span class="tag ${granted ? 'tag-success' : 'tag-info'}" style="margin-left:8px;">
                            ${granted ? '✓ 已授权' : '✗ 未授权'}
                        </span>
                    </div>`;
            });
        } else {
            const granted = r.funcPermissions && r.funcPermissions.includes(m.key);
            funcHtml += `
                <div style="display:flex;align-items:center;padding:4px 0;border-bottom:1px solid #f0f0f0;">
                    <span style="width:140px;color:#606266;font-size:13px;">${m.module}</span>
                    <span class="tag ${granted ? 'tag-success' : 'tag-info'}" style="margin-left:8px;">
                        ${granted ? '✓ 已授权' : '✗ 未授权'}
                    </span>
                </div>`;
        }
    });

    /* 数据权限机构范围 */
    let dataScopeHtml = '';
    if (r.dataScope === 'all') {
        dataScopeHtml = '<span class="tag tag-primary">全部机构数据</span>';
    } else if (r.dataScope === 'org' || r.dataScope === 'dept') {
        const label = r.dataScope === 'org' ? '本机构数据' : '本部门数据';
        const orgs  = (r.dataScopeOrgIds || []).map(oid => {
            const o = (mockData.orgs || []).find(x => x.id === oid);
            return o ? o.orgName : `机构${oid}`;
        });
        dataScopeHtml = `<span class="tag tag-warning">${label}</span>` +
            (orgs.length ? `<span style="margin-left:8px;font-size:13px;color:#606266;">限定范围：${orgs.join('、')}</span>` : '');
    }

    const html = `
        <div class="modal-header"><h3>角色详情</h3></div>
        <div class="modal-body" style="max-height:72vh;overflow-y:auto;">
            <!-- 基础信息 -->
            <div class="detail-section" style="margin-bottom:18px;">
                <div class="detail-section-title" style="font-weight:600;color:#303133;margin-bottom:12px;padding-bottom:6px;border-bottom:2px solid #409eff;">基础信息</div>
                <div class="detail-grid" style="display:grid;grid-template-columns:1fr 1fr;gap:10px;">
                    <div><span style="color:#909399;">角色名称：</span><strong>${r.roleName}</strong></div>
                    <div><span style="color:#909399;">角色编码：</span><code style="background:#f5f7fa;padding:2px 6px;border-radius:3px;">${r.roleCode}</code></div>
                    <div><span style="color:#909399;">所属分组：</span>${r.groupName}</div>
                    <div><span style="color:#909399;">状态：</span>${r.status === 'active' ? '<span class="tag tag-success">启用</span>' : '<span class="tag tag-info">禁用</span>'}</div>
                    <div><span style="color:#909399;">已授权模块：</span>${permCount} 个</div>
                    <div><span style="color:#909399;">创建时间：</span>${r.createdAt}</div>
                    <div style="grid-column:1/-1;"><span style="color:#909399;">备注：</span>${r.remark || '-'}</div>
                </div>
            </div>
            <!-- 数据权限 -->
            <div class="detail-section" style="margin-bottom:18px;">
                <div class="detail-section-title" style="font-weight:600;color:#303133;margin-bottom:12px;padding-bottom:6px;border-bottom:2px solid #409eff;">数据权限</div>
                <div>${dataScopeHtml}</div>
            </div>
            <!-- 功能权限 -->
            <div class="detail-section">
                <div class="detail-section-title" style="font-weight:600;color:#303133;margin-bottom:12px;padding-bottom:6px;border-bottom:2px solid #409eff;">功能权限（共 ${permCount} 个已授权模块）</div>
                ${funcHtml}
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关 闭</button>
            <button class="btn btn-primary" onclick="closeModal();openRoleModal(${r.id})">编 辑</button>
        </div>`;
    showModal(html, '600px');
}

/* ============================================================
   新增/编辑角色弹窗（3-Tab）
   ============================================================ */
function openRoleModal(id) {
    window.rpModalTab  = 'base';
    window._rpEditId   = id;
    const r = id ? mockData.roles.find(x => x.id === id) : null;

    const html = `
        <div class="modal-header"><h3>${r ? '编辑角色' : '新增角色'}</h3></div>
        <div class="modal-body" style="padding:0;">
            <!-- 内嵌 Tab 导航 -->
            <div id="rpMTabs" style="display:flex;border-bottom:1px solid #e4e7ed;padding:0 20px;">
                <div id="rpMTabBase" onclick="rpModalSwitchTab('base')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid transparent;margin-bottom:-1px;
                            color:#409eff;border-bottom-color:#409eff;font-weight:500;">
                    基础信息
                </div>
                <div id="rpMTabFunc" onclick="rpModalSwitchTab('func')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid transparent;margin-bottom:-1px;
                            color:#606266;">
                    功能权限
                </div>
                <div id="rpMTabData" onclick="rpModalSwitchTab('data')"
                     style="padding:12px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid transparent;margin-bottom:-1px;
                            color:#606266;">
                    数据权限
                </div>
            </div>
            <!-- Tab 内容 -->
            <div id="rpMTabContent" style="padding:20px;min-height:280px;max-height:60vh;overflow-y:auto;">
                ${_buildRoleBaseTab(r)}
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取 消</button>
            <button class="btn btn-primary" onclick="saveRole(${id || 'null'})">保 存</button>
        </div>`;
    showModal(html, '680px');
}

function rpModalSwitchTab(tab) {
    window.rpModalTab = tab;
    /* 更新 Tab 样式 */
    ['base','func','data'].forEach(t => {
        const el = document.getElementById('rpMTab' + t.charAt(0).toUpperCase() + t.slice(1));
        if (el) {
            el.style.color              = t === tab ? '#409eff' : '#606266';
            el.style.borderBottomColor  = t === tab ? '#409eff' : 'transparent';
            el.style.fontWeight         = t === tab ? '500' : 'normal';
        }
    });
    /* 渲染内容 */
    const id = window._rpEditId;
    const r  = id ? mockData.roles.find(x => x.id === id) : null;
    const content = document.getElementById('rpMTabContent');
    if (tab === 'base')  content.innerHTML = _buildRoleBaseTab(r);
    if (tab === 'func')  content.innerHTML = _buildRoleFuncTab(r);
    if (tab === 'data')  content.innerHTML = _buildRoleDataTab(r);
}

/* ---- 基础信息 Tab ---- */
function _buildRoleBaseTab(r) {
    return `
        <div class="form-row">
            <div class="form-group" style="flex:1;">
                <label class="form-label required">角色名称</label>
                <input id="rpRoleName" class="form-control" placeholder="请输入角色名称" value="${r ? r.roleName : ''}">
            </div>
            <div class="form-group" style="flex:1;">
                <label class="form-label required">角色编码</label>
                <input id="rpRoleCode" class="form-control" placeholder="如 PURCHASER" value="${r ? r.roleCode : ''}">
            </div>
        </div>
        <div class="form-row">
            <div class="form-group" style="flex:1;">
                <label class="form-label required">所属分组</label>
                <select id="rpRoleGroupId" class="form-control">
                    <option value="">请选择分组</option>
                    ${mockData.roleGroups.map(g =>
                        `<option value="${g.id}" ${r && r.groupId === g.id ? 'selected' : ''}>${g.groupName}</option>`
                    ).join('')}
                </select>
            </div>
            <div class="form-group" style="width:120px;">
                <label class="form-label">状态</label>
                <select id="rpRoleStatusSel" class="form-control">
                    <option value="active"   ${(!r || r.status==='active')   ? 'selected' : ''}>启用</option>
                    <option value="inactive" ${(r  && r.status==='inactive') ? 'selected' : ''}>禁用</option>
                </select>
            </div>
        </div>
        <div class="form-group">
            <label class="form-label">备注</label>
            <textarea id="rpRoleRemark" class="form-control" rows="2" placeholder="请输入角色说明">${r ? r.remark : ''}</textarea>
        </div>`;
}

/* ---- 功能权限 Tab ---- */
function _buildRoleFuncTab(r) {
    const granted = new Set(r ? (r.funcPermissions || []) : []);
    let html = `
        <div style="margin-bottom:10px;display:flex;gap:10px;align-items:center;">
            <span style="font-size:13px;color:#606266;">勾选需授权的功能模块：</span>
            <button class="btn btn-sm btn-default" onclick="_rpFuncSelectAll(true)">全选</button>
            <button class="btn btn-sm btn-default" onclick="_rpFuncSelectAll(false)">全不选</button>
        </div>`;

    FUNC_MODULE_CONFIG.forEach(m => {
        html += `<div style="margin-bottom:12px;">
            <div style="font-weight:600;color:#303133;margin-bottom:6px;padding:4px 8px;background:#f5f7fa;border-radius:4px;">${m.module}</div>
            <div style="display:flex;flex-wrap:wrap;gap:8px;padding:0 8px;">`;

        if (m.children) {
            m.children.forEach(c => {
                html += `
                    <label style="display:flex;align-items:center;gap:4px;cursor:pointer;min-width:140px;">
                        <input type="checkbox" class="rpFuncChk" value="${c.key}" ${granted.has(c.key) ? 'checked' : ''}>
                        <span style="font-size:13px;">${c.label}</span>
                    </label>`;
            });
        } else {
            html += `
                <label style="display:flex;align-items:center;gap:4px;cursor:pointer;min-width:140px;">
                    <input type="checkbox" class="rpFuncChk" value="${m.key}" ${granted.has(m.key) ? 'checked' : ''}>
                    <span style="font-size:13px;">${m.module}</span>
                </label>`;
        }
        html += '</div></div>';
    });
    return html;
}

function _rpFuncSelectAll(checked) {
    document.querySelectorAll('.rpFuncChk').forEach(c => { c.checked = checked; });
}

/* ---- 数据权限 Tab ---- */
function _buildRoleDataTab(r) {
    const scope   = r ? r.dataScope : 'all';
    const orgIds  = new Set(r ? (r.dataScopeOrgIds || []) : []);
    const orgList = (mockData.orgs || []).filter(o => o.type !== 'dept' || !o.parentId);

    const orgOptions = (mockData.orgs || []).map(o =>
        `<label style="display:flex;align-items:center;gap:4px;cursor:pointer;min-width:160px;">
            <input type="checkbox" class="rpDataOrgChk" value="${o.id}" ${orgIds.has(o.id) ? 'checked' : ''}>
            <span style="font-size:13px;">${o.orgName}</span>
        </label>`).join('');

    return `
        <div style="margin-bottom:14px;">
            <label class="form-label">数据范围</label>
            <div style="display:flex;gap:24px;margin-top:6px;">
                <label style="display:flex;align-items:center;gap:6px;cursor:pointer;">
                    <input type="radio" name="rpDataScope" value="all"  ${scope==='all'  ? 'checked' : ''} onchange="rpToggleOrgScope(this.value)">
                    <span>全部数据</span>
                </label>
                <label style="display:flex;align-items:center;gap:6px;cursor:pointer;">
                    <input type="radio" name="rpDataScope" value="org"  ${scope==='org'  ? 'checked' : ''} onchange="rpToggleOrgScope(this.value)">
                    <span>本机构数据</span>
                </label>
                <label style="display:flex;align-items:center;gap:6px;cursor:pointer;">
                    <input type="radio" name="rpDataScope" value="dept" ${scope==='dept' ? 'checked' : ''} onchange="rpToggleOrgScope(this.value)">
                    <span>本部门数据</span>
                </label>
            </div>
        </div>
        <div id="rpOrgScopeBox" style="display:${scope!=='all' ? 'block' : 'none'};">
            <label class="form-label">限定机构/部门范围</label>
            <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:6px;padding:10px;background:#f5f7fa;border-radius:6px;max-height:160px;overflow-y:auto;">
                ${orgOptions || '<span style="color:#909399;font-size:13px;">暂无机构数据</span>'}
            </div>
        </div>`;
}

function rpToggleOrgScope(val) {
    const box = document.getElementById('rpOrgScopeBox');
    if (box) box.style.display = val !== 'all' ? 'block' : 'none';
}

/* ============================================================
   保存角色
   ============================================================ */
function saveRole(id) {
    /* 基础信息 */
    const nameEl    = document.getElementById('rpRoleName');
    const codeEl    = document.getElementById('rpRoleCode');
    const groupEl   = document.getElementById('rpRoleGroupId');
    const statusEl  = document.getElementById('rpRoleStatusSel');
    const remarkEl  = document.getElementById('rpRoleRemark');

    if (!nameEl || !codeEl || !groupEl) {
        showToast('请先完善基础信息', 'error'); return;
    }
    const roleName = nameEl.value.trim();
    const roleCode = codeEl.value.trim().toUpperCase();
    const groupId  = parseInt(groupEl.value);
    const status   = statusEl ? statusEl.value : 'active';
    const remark   = remarkEl ? remarkEl.value.trim() : '';

    if (!roleName) { showToast('请输入角色名称', 'error'); return; }
    if (!roleCode) { showToast('请输入角色编码', 'error'); return; }
    if (!groupId)  { showToast('请选择所属分组', 'error'); return; }

    /* 功能权限（若未切到功能权限 Tab 则保留原值） */
    const chkList = document.querySelectorAll('.rpFuncChk');
    let funcPermissions;
    if (chkList.length > 0) {
        funcPermissions = Array.from(chkList).filter(c => c.checked).map(c => c.value);
    } else {
        const orig = id ? mockData.roles.find(x => x.id === id) : null;
        funcPermissions = orig ? (orig.funcPermissions || []) : [];
    }

    /* 数据权限 */
    const scopeRadio = document.querySelector('input[name="rpDataScope"]:checked');
    let dataScope = scopeRadio ? scopeRadio.value : 'all';
    const orgChks = document.querySelectorAll('.rpDataOrgChk');
    let dataScopeOrgIds = [];
    if (orgChks.length > 0 && dataScope !== 'all') {
        dataScopeOrgIds = Array.from(orgChks).filter(c => c.checked).map(c => parseInt(c.value));
    } else if (id) {
        const orig = mockData.roles.find(x => x.id === id);
        if (orig && !scopeRadio) {
            dataScope = orig.dataScope;
            dataScopeOrgIds = orig.dataScopeOrgIds || [];
        }
    }

    const group = mockData.roleGroups.find(g => g.id === groupId);
    const groupName = group ? group.groupName : '';

    if (id) {
        const r = mockData.roles.find(x => x.id === id);
        Object.assign(r, { roleName, roleCode, groupId, groupName, status, remark, funcPermissions, dataScope, dataScopeOrgIds });
        showToast('角色编辑成功');
    } else {
        const newId = Math.max(0, ...mockData.roles.map(x => x.id)) + 1;
        mockData.roles.push({
            id: newId, roleName, roleCode, groupId, groupName, status, remark,
            funcPermissions, dataScope, dataScopeOrgIds,
            createdAt: new Date().toLocaleString('zh-CN').replace(/\//g, '-')
        });
        showToast('角色新增成功');
    }
    closeModal();
    renderRolePermissionPage(document.getElementById('mainContent'));
}

/* ============================================================
   删除角色
   ============================================================ */
function deleteRole(id) {
    const r = mockData.roles.find(x => x.id === id);
    if (!r) return;
    const html = `
        <div class="modal-header"><h3>⚠️ 删除确认</h3></div>
        <div class="modal-body">
            <p style="margin:10px 0;">确定要删除角色「<strong>${r.roleName}</strong>」吗？此操作不可恢复。</p>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取 消</button>
            <button class="btn btn-danger"  onclick="_doDeleteRole(${id})">确认删除</button>
        </div>`;
    showModal(html, '420px');
}

function _doDeleteRole(id) {
    mockData.roles = mockData.roles.filter(x => x.id !== id);
    closeModal();
    showToast('角色已删除');
    renderRolePermissionPage(document.getElementById('mainContent'));
}
