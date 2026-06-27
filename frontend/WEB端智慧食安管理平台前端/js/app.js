/**
 * app.js — 应用初始化入口
 *
 * 说明：
 *  - 页面加载完成后执行 init() 函数
 *  - 初始化侧边栏、渲染默认页面（仓库信息管理）
 *  - 绑定全局事件（点击空白处关闭 AI 提示框）
 */

/* ============================================================
   应用初始化
   ============================================================ */
function init() {
    // 渲染侧边栏导航（默认激活 warehouse 页面）
    renderSidebar();

    // 渲染主内容区（默认显示仓库信息管理）
    renderPage(currentPage);

    // 全局事件：点击 AI 提示框外部时关闭提示框
    document.addEventListener('click', function (e) {
        const tooltip = document.getElementById('aiTooltip');
        // 如果点击的不是 AI 建议按钮，则隐藏提示框
        if (!e.target.classList.contains('ai-suggest-btn')) {
            tooltip.classList.remove('show');
        }
    });
}

/* 页面加载完成后自动执行初始化 */
init();
