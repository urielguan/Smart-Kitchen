/**
 * sidebar.js — 侧边栏导航渲染 & 页面切换
 *
 * 包含：
 *  - menuConfig：侧边栏菜单结构配置
 *  - renderSidebar()：根据 currentPage 渲染侧边栏高亮状态
 *  - switchPage()：切换页面（更新状态 + 面包屑 + 重新渲染）
 *  - renderPage()：根据 page 标识分发到对应模块渲染函数
 */

/* ============================================================
   侧边栏菜单配置
   ============================================================ */
const menuConfig = [
    {
        group: '数据概览',
        items: [
            { label: '数据看板', page: 'dashboard', icon: '📊' }
        ]
    },
    {
        group: '采购管理',
        items: [
            { label: '供应商管理', page: 'supplier',     icon: '🏢' },
            { label: '采购计划',   page: 'purchasePlan', icon: '📑' },
            { label: '采购订单',   page: 'purchase',     icon: '📋' }
        ]
    },
    {
        group: '仓储管理',
        items: [
            { label: '仓库信息管理', page: 'warehouse', icon: '🏭' },
            /* badge: 3 表示物料预警角标 */
            { label: '物料信息管理', page: 'material',  icon: '📦', badge: 3 },
            { label: '库存汇总',     page: 'inventory', icon: '📊' },
            { label: '入库管理',     page: 'inbound',   icon: '📥' },
            { label: '出库管理',     page: 'outbound',  icon: '📤' },

            { label: '盘点管理',     page: 'stocktake', icon: '📝' }
        ]
    },
    {
        group: '菜谱营养',
        items: [
            { label: '菜谱库管理', page: 'recipe', icon: '🍽️' },
            { label: '菜谱计划',   page: 'plan',   icon: '📅' }
        ]
    },
    {
        group: '后厨管理',
        items: [
            { label: '烹饪记录',     page: 'cook',        icon: '👨‍🍳' },
            { label: '留样管理',     page: 'sample',      icon: '🧪'  },
            { label: '智能人脸晨检', page: 'healthCheck', icon: '🤖'  },
            { label: '视频监控管理', page: 'cctv',        icon: '📷'  },
            { label: '设备管理',     page: 'device',      icon: '🔧'  }
        ]
    },
    {
        group: '系统管理',
        items: [
            { label: '组织管理',     page: 'org',            icon: '🏗️' },
            { label: '员工管理',     page: 'employee',       icon: '👤' },
            { label: '角色权限管理', page: 'rolePermission', icon: '🔐' },
            { label: '评价管理',     page: 'review',         icon: '⭐' }
        ]
    }
];

/* ============================================================
   渲染侧边栏
   ============================================================ */
function renderSidebar() {
    const container = document.getElementById('sidebarMenu');
    let html = '';

    menuConfig.forEach(group => {
        html += `<div class="menu-group">
            <div class="menu-group-title">${group.group}</div>`;

        group.items.forEach(item => {
            const isActive = item.page === currentPage ? 'active' : '';
            const badge    = item.badge
                ? `<span class="nav-badge">${item.badge}</span>`
                : '';

            html += `<div class="nav-item ${isActive}"
                         onclick="switchPage('${item.page}', '${group.group}', '${item.label}')">
                        <span>${item.icon} ${item.label}</span>
                        ${badge}
                     </div>`;
        });

        html += '</div>';
    });

    container.innerHTML = html;
}

/* ============================================================
   切换页面
   ============================================================ */
function switchPage(page, group, label) {
    currentPage = page;
    // 更新面包屑
    document.getElementById('breadcrumb').textContent = `${group} / ${label}`;
    // 重新渲染侧边栏高亮
    renderSidebar();
    // 渲染主内容
    renderPage(page);
}

/* ============================================================
   页面内容分发
   ============================================================ */
function renderPage(page) {
    const content = document.getElementById('mainContent');

    if (page === 'warehouse') {
        renderWarehousePage(content);
    } else if (page === 'material') {
        renderMaterialPage(content);
    } else if (page === 'recipe') {
        renderRecipePage(content);
    } else if (page === 'org') {
        renderOrgPage(content);
    } else if (page === 'supplier') {
        renderSupplierPage(content);
    } else if (page === 'purchase') {
        renderPurchasePage(content);
    } else if (page === 'purchasePlan') {
        renderPurchasePlanPage(content);
    } else if (page === 'inventory') {
        renderInventoryPage(content);
    } else if (page === 'inbound') {
        renderInboundPage(content);
    } else if (page === 'outbound') {
        renderOutboundPage(content);
    } else if (page === 'stocktake') {
        renderStocktakePage(content);
    } else if (page === 'plan') {
        renderRecipePlanPage(content);
    } else if (page === 'cook') {
        renderCookPage(content);
    } else if (page === 'sample') {
        renderSamplePage(content);
    } else if (page === 'healthCheck') {
        renderHealthCheckPage(content);
    } else if (page === 'cctv') {
        renderCctvPage(content);
    } else if (page === 'device') {
        renderDevicePage(content);
    } else if (page === 'employee') {
        renderEmployeePage(content);
    } else if (page === 'rolePermission') {
        renderRolePermissionPage(content);
    } else if (page === 'review') {
        renderReviewPage(content);
    } else {
        // 其余板块显示占位页面
        const placeholderMap = {
            dashboard: { icon: '📊', title: '数据看板' },
            supplier:  { icon: '🏢', title: '供应商管理' },
            purchase:  { icon: '📋', title: '采购订单' },
            inbound:   { icon: '📥', title: '入库管理' },
            outbound:  { icon: '📤', title: '出库管理' },
            stocktake: { icon: '📝', title: '盘点管理' },
            plan:      { icon: '📅', title: '菜谱计划' },
            cook:      { icon: '👨‍🍳', title: '烹饪记录' },
            sample:    { icon: '🧪', title: '留样管理' },
            org:       { icon: '🏗️', title: '组织管理' },
            employee:  { icon: '👤', title: '员工管理' }
        };
        const p = placeholderMap[page] || { icon: '🔧', title: page };
        content.innerHTML = `
            <div class="placeholder-page">
                <div class="placeholder-icon">${p.icon}</div>
                <div class="placeholder-title">${p.title}</div>
                <div class="placeholder-desc">该模块正在建设中，敬请期待</div>
            </div>`;
    }
}
