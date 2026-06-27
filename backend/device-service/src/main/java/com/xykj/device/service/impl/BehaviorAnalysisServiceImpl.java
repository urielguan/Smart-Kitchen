package com.xykj.device.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.BehaviorAnalysisQueryDTO;
import com.xykj.device.service.BehaviorAnalysisService;
import com.xykj.device.vo.BehaviorAnalysisVO;
import com.xykj.device.vo.BehaviorIssueVO;
import com.xykj.device.vo.BehaviorStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI人员行为分析服务实现
 *
 * TODO: 实际实现需要对接AI分析平台或数据库
 * 当前为Mock实现，返回模拟数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorAnalysisServiceImpl implements BehaviorAnalysisService {

    private final Random random = new Random();

    // 员工姓名列表
    private static final String[] EMPLOYEE_NAMES = {
            "张三", "李四", "王五", "赵六", "钱七",
            "孙八", "周九", "吴十", "郑十一", "王小明",
            "李小红", "张伟", "刘芳", "陈静", "杨洋"
    };

    // 岗位列表
    private static final String[] ROLES = {
            "厨师长", "炒锅厨师", "切配厨师", "面点师", "洗碗工", "配菜员"
    };

    // 部门列表
    private static final String[] DEPARTMENTS = {
            "热菜组", "冷菜组", "面点组", "洗涤组", "备菜组"
    };

    // 问题类型
    private static final String[][] ISSUE_TYPES = {
            {"操作规范", "未按标准流程操作", "建议加强SOP培训"},
            {"卫生习惯", "未及时洗手消毒", "建议强化卫生意识培训"},
            {"效率问题", "操作速度较慢", "建议进行技能提升训练"},
            {"安全问题", "未佩戴防护用品", "建议加强安全意识教育"},
            {"协作问题", "团队沟通不足", "建议参加团队协作培训"}
    };

    @Override
    public Page<BehaviorAnalysisVO> getBehaviorList(BehaviorAnalysisQueryDTO query) {
        // TODO: 实际实现应该从数据库查询
        List<BehaviorAnalysisVO> mockList = generateMockBehaviorList(query);

        // 分页处理
        int total = mockList.size();
        int fromIndex = (query.getPageNum() - 1) * query.getPageSize();
        int toIndex = Math.min(fromIndex + query.getPageSize(), total);

        List<BehaviorAnalysisVO> pageList = fromIndex < total ?
                mockList.subList(fromIndex, toIndex) : new ArrayList<>();

        Page<BehaviorAnalysisVO> result = new Page<>(query.getPageNum(), query.getPageSize());
        result.setRecords(pageList);
        result.setTotal(total);
        return result;
    }

    @Override
    public BehaviorAnalysisVO getBehaviorDetail(Long id) {
        // TODO: 实际实现应该从数据库查询
        return generateMockBehaviorDetail(id);
    }

    @Override
    public BehaviorAnalysisVO getEmployeeBehaviorDetail(Long employeeId) {
        // TODO: 实际实现应该从数据库查询
        return generateMockBehaviorDetail(employeeId);
    }

    @Override
    public BehaviorStatisticsVO getBehaviorStatistics(Long orgId) {
        // TODO: 实际实现应该从数据库统计
        BehaviorStatisticsVO vo = new BehaviorStatisticsVO();
        vo.setTotalEmployees(45L);
        vo.setAverageEfficiency(82.5);
        vo.setAverageCompliance(88.3);
        vo.setAverageHygiene(85.6);
        vo.setNeedImprovementCount(8L);
        vo.setBenchmarkCount(12L);
        vo.setTodayAnalysisCount(156L);
        vo.setIssueCount(23L);

        // 效率分布
        BehaviorStatisticsVO.ScoreDistribution efficiencyDist = new BehaviorStatisticsVO.ScoreDistribution();
        efficiencyDist.setExcellent(10L);
        efficiencyDist.setGood(15L);
        efficiencyDist.setAverage(12L);
        efficiencyDist.setPoor(5L);
        efficiencyDist.setFail(3L);
        vo.setEfficiencyDistribution(efficiencyDist);

        // 合规分布
        BehaviorStatisticsVO.ScoreDistribution complianceDist = new BehaviorStatisticsVO.ScoreDistribution();
        complianceDist.setExcellent(15L);
        complianceDist.setGood(18L);
        complianceDist.setAverage(8L);
        complianceDist.setPoor(3L);
        complianceDist.setFail(1L);
        vo.setComplianceDistribution(complianceDist);

        // 卫生分布
        BehaviorStatisticsVO.ScoreDistribution hygieneDist = new BehaviorStatisticsVO.ScoreDistribution();
        hygieneDist.setExcellent(12L);
        hygieneDist.setGood(16L);
        hygieneDist.setAverage(10L);
        hygieneDist.setPoor(5L);
        hygieneDist.setFail(2L);
        vo.setHygieneDistribution(hygieneDist);

        return vo;
    }

    // ========== Mock数据生成 ==========

    private List<BehaviorAnalysisVO> generateMockBehaviorList(BehaviorAnalysisQueryDTO query) {
        List<BehaviorAnalysisVO> list = new ArrayList<>();

        for (int i = 0; i < 15; i++) {
            BehaviorAnalysisVO vo = new BehaviorAnalysisVO();
            vo.setId((long) (i + 1));
            vo.setEmployeeId((long) (i + 1));
            vo.setEmployeeName(EMPLOYEE_NAMES[i % EMPLOYEE_NAMES.length]);
            vo.setEmployeeCode("EMP" + String.format("%04d", i + 1));
            vo.setEmployeeRole(ROLES[i % ROLES.length]);
            vo.setDepartment(DEPARTMENTS[i % DEPARTMENTS.length]);
            vo.setAvatar("/static/avatars/avatar_" + (i % 10) + ".jpg");

            // 生成评分
            int efficiency = 60 + random.nextInt(40);
            int compliance = 65 + random.nextInt(35);
            int hygiene = 70 + random.nextInt(30);

            vo.setEfficiencyScore(efficiency);
            vo.setComplianceScore(compliance);
            vo.setHygieneScore(hygiene);
            vo.setOverallScore((efficiency + compliance + hygiene) / 3);

            vo.setWorkDuration(480 + random.nextInt(120)); // 8-10小时
            vo.setOperationCount(50 + random.nextInt(100));
            vo.setViolationCount(random.nextInt(5));

            // 判断是否有问题
            boolean hasIssues = efficiency < 70 || compliance < 70 || hygiene < 70 || vo.getViolationCount() > 2;
            vo.setHasIssues(hasIssues);

            // 生成标签
            List<String> tags = new ArrayList<>();
            if (efficiency >= 85) tags.add("高效");
            if (compliance >= 85) tags.add("合规");
            if (hygiene >= 85) tags.add("卫生");
            if (vo.getViolationCount() == 0) tags.add("零违规");
            if (hasIssues) tags.add("需关注");
            vo.setTags(tags);

            vo.setPeriodStart(LocalDateTime.now().minusDays(1));
            vo.setPeriodEnd(LocalDateTime.now());
            vo.setCreatedAt(LocalDateTime.now().minusHours(random.nextInt(24)));

            list.add(vo);
        }

        // 如果查询有问题的员工
        if (Boolean.TRUE.equals(query.getHasIssues())) {
            list = list.stream().filter(BehaviorAnalysisVO::getHasIssues).toList();
        }

        // 按综合评分排序
        list.sort((a, b) -> b.getOverallScore().compareTo(a.getOverallScore()));

        return list;
    }

    private BehaviorAnalysisVO generateMockBehaviorDetail(Long id) {
        BehaviorAnalysisVO vo = new BehaviorAnalysisVO();
        vo.setId(id);
        vo.setEmployeeId(id);
        vo.setEmployeeName(EMPLOYEE_NAMES[(int) (id % EMPLOYEE_NAMES.length)]);
        vo.setEmployeeCode("EMP" + String.format("%04d", id));
        vo.setEmployeeRole(ROLES[(int) (id % ROLES.length)]);
        vo.setDepartment(DEPARTMENTS[(int) (id % DEPARTMENTS.length)]);
        vo.setAvatar("/static/avatars/avatar_" + (id % 10) + ".jpg");

        // 生成评分
        int efficiency = 60 + random.nextInt(40);
        int compliance = 65 + random.nextInt(35);
        int hygiene = 70 + random.nextInt(30);
        int punctuality = 75 + random.nextInt(25);
        int teamwork = 70 + random.nextInt(30);

        vo.setEfficiencyScore(efficiency);
        vo.setComplianceScore(compliance);
        vo.setHygieneScore(hygiene);
        vo.setPunctualityScore(punctuality);
        vo.setTeamworkScore(teamwork);
        vo.setOverallScore((efficiency + compliance + hygiene + punctuality + teamwork) / 5);

        vo.setWorkDuration(480 + random.nextInt(120));
        vo.setOperationCount(50 + random.nextInt(100));
        vo.setViolationCount(random.nextInt(5));

        boolean hasIssues = efficiency < 70 || compliance < 70 || hygiene < 70;
        vo.setHasIssues(hasIssues);

        // 生成标签
        List<String> tags = new ArrayList<>();
        if (efficiency >= 85) tags.add("高效员工");
        if (compliance >= 85) tags.add("合规标兵");
        if (hygiene >= 85) tags.add("卫生模范");
        if (punctuality >= 90) tags.add("全勤员工");
        vo.setTags(tags);

        // 生成问题列表
        List<BehaviorIssueVO> issues = new ArrayList<>();
        if (hasIssues) {
            int issueCount = 1 + random.nextInt(3);
            Set<Integer> usedTypes = new HashSet<>();
            for (int i = 0; i < issueCount; i++) {
                int typeIndex;
                do {
                    typeIndex = random.nextInt(ISSUE_TYPES.length);
                } while (usedTypes.contains(typeIndex));
                usedTypes.add(typeIndex);

                BehaviorIssueVO issue = new BehaviorIssueVO();
                issue.setId((long) (i + 1));
                issue.setIssueType(ISSUE_TYPES[typeIndex][0]);
                issue.setIssueName(ISSUE_TYPES[typeIndex][0] + "问题");
                issue.setDescription(ISSUE_TYPES[typeIndex][1]);
                issue.setOccurrenceCount(1 + random.nextInt(5));
                issue.setSuggestion(ISSUE_TYPES[typeIndex][2]);
                issues.add(issue);
            }
        }
        vo.setIssues(issues);

        // 生成AI培训建议
        List<String> suggestions = new ArrayList<>();
        if (efficiency < 75) {
            suggestions.add("建议参加《厨房操作效率提升》培训课程");
        }
        if (compliance < 75) {
            suggestions.add("建议学习《食品安全操作规范》相关内容");
        }
        if (hygiene < 75) {
            suggestions.add("建议加强个人卫生习惯养成训练");
        }
        if (hasIssues) {
            suggestions.add("建议安排一对一辅导，针对性改进问题");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("表现优秀，建议继续保持并分享经验");
            suggestions.add("可考虑作为新员工培训导师");
        }
        vo.setAiTrainingSuggestions(suggestions);

        vo.setPeriodStart(LocalDateTime.now().minusDays(7));
        vo.setPeriodEnd(LocalDateTime.now());
        vo.setCreatedAt(LocalDateTime.now().minusHours(random.nextInt(24)));

        return vo;
    }
}
