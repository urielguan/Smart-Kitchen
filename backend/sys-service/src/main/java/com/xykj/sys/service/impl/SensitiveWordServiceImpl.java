package com.xykj.sys.service.impl;

import com.xykj.sys.mapper.SensitiveWordMapper;
import com.xykj.sys.service.SensitiveWordService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词过滤服务实现（DFA算法）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;

    /** 结束标记字符（\0不会出现在正常文本中） */
    private static final Character END_MARKER = '\0';

    /** DFA字典树根节点 */
    private volatile Map<Character, Object> wordMap = new HashMap<>();

    @PostConstruct
    public void init() {
        refreshWordMap();
    }

    @Override
    @Scheduled(fixedDelay = 300000)
    public void refreshWordMap() {
        try {
            List<String> words = sensitiveWordMapper.selectAllActiveWords();
            Map<Character, Object> newMap = new HashMap<>(words.size());
            for (String word : words) {
                if (word == null || word.isEmpty()) continue;
                addWord(newMap, word);
            }
            this.wordMap = newMap;
            log.info("敏感词库刷新完成，共加载 {} 个敏感词", words.size());
        } catch (Exception e) {
            log.error("敏感词库刷新失败", e);
        }
    }

    @Override
    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) return false;
        // 去除空格、数字、标点等干扰字符后再匹配
        String cleaned = cleanText(text);
        Map<Character, Object> currentMap = this.wordMap;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            @SuppressWarnings("unchecked")
            Map<Character, Object> node = (Map<Character, Object>) currentMap.get(c);
            if (node == null) {
                continue;
            }
            // 匹配到起始字符，检查是否是一个完整词（单字敏感词）
            if (Boolean.TRUE.equals(node.get(END_MARKER))) {
                return true;
            }
            // 继续往下匹配更长的词
            for (int j = i + 1; j < cleaned.length(); j++) {
                char next = cleaned.charAt(j);
                @SuppressWarnings("unchecked")
                Map<Character, Object> nextNode = (Map<Character, Object>) node.get(next);
                if (nextNode == null) break;
                if (Boolean.TRUE.equals(nextNode.get(END_MARKER))) {
                    return true;
                }
                node = nextNode;
            }
        }
        return false;
    }

    /**
     * 清除文本中的干扰字符（空格、数字、常见标点符号、特殊符号），
     * 使 "蠢 猪"、"蠢1猪"、"蠢.猪" 等变体能被 DFA 匹配到原始敏感词。
     */
    private String cleanText(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 保留中文、英文字母
            if (isChinese(c) || isEnglish(c)) {
                sb.append(Character.toLowerCase(c));
            }
            // 跳过空格、数字、标点、特殊符号等干扰字符
        }
        return sb.toString();
    }

    private boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }

    private boolean isEnglish(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * 将一个敏感词加入DFA字典树
     */
    @SuppressWarnings("unchecked")
    private void addWord(Map<Character, Object> root, String word) {
        Map<Character, Object> current = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Object obj = current.get(c);
            if (obj == null) {
                Map<Character, Object> newNode = new HashMap<>();
                current.put(c, newNode);
                current = newNode;
            } else {
                current = (Map<Character, Object>) obj;
            }
            // 最后一个字符标记结束
            if (i == word.length() - 1) {
                current.put(END_MARKER, true);
            }
        }
    }
}
