package com.Myself.demo.service;

import com.nlf.calendar.JieQi;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class EightCharactersService {

    private static final String[] STEMS = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] BRANCHES = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] ANIMALS = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};
    private static final String[] STEM_ELEMENTS = {"木", "木", "火", "火", "土", "土", "金", "金", "水", "水"};
    private static final String[] BRANCH_ELEMENTS = {"水", "土", "木", "木", "土", "火", "火", "土", "金", "金", "土", "水"};
    private static final int[] HOUR_BRANCH = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11};

    private static final int[][] BRANCH_HIDDEN_STEMS = {
        {9},
        {5, 9, 7},
        {0, 2, 4},
        {1},
        {4, 1, 9},
        {2, 6, 4},
        {3, 5},
        {5, 3, 1},
        {6, 8, 4},
        {7},
        {4, 7, 3},
        {8, 0},
    };

    private static final String[] SHI_SHEN = {"比肩", "劫财", "食神", "伤官", "偏财", "正财", "七杀", "正官", "偏印", "正印"};

    private static final String[] NAYIN = {
        "海中金", "炉中火", "大林木", "路旁土", "剑锋金",
        "山头火", "涧下水", "城头土", "白蜡金", "杨柳木",
        "井泉水", "屋上土", "霹雳火", "松柏木", "长流水",
        "沙中金", "山下火", "平地木", "壁上土", "金箔金",
        "覆灯火", "天河水", "大驿土", "钗钏金", "桑柘木",
        "大溪水", "沙中土", "天上火", "石榴木", "大海水"
    };

    public String calculate(int year, int month, int day, int hour, int minute, String gender) {
        try {
            boolean isLateZi = (hour == 23);
            int[][] p = calcPillars(year, month, day, hour);
            int[] stems = p[0];
            int[] branches = p[1];
            return buildResult(stems[2], stems, branches, year, month, day, hour, minute, isLateZi)
                   + calcDaYun(year, month, day, stems[0], stems[1], branches[1], gender);
        } catch (Exception e) {
            log.warn("八字计算失败: year={}, month={}, day={}", year, month, day, e);
            return "八字推算失败，请检查日期是否有效";
        }
    }

    private String calcDaYun(int year, int month, int day, int yStem, int mStem, int mBranch, String gender) {
        boolean isYang = yStem % 2 == 0;
        boolean isMale = "男".equals(gender);
        boolean forward = (isYang && isMale) || (!isYang && !isMale);

        Solar birth = Solar.fromYmd(year, month, day);
        Lunar lunar = birth.getLunar();
        JieQi jie = forward ? lunar.getNextJie() : lunar.getPrevJie();
        Solar jieSolar = jie.getSolar();
        int daysDiff = Math.abs(jieSolar.subtract(birth));
        int qiYunAge = daysDiff / 3;

        StringBuilder sb = new StringBuilder();
        sb.append("\n【大运】\n");
        sb.append(forward ? "顺排" : "逆排").append("  ").append(isYang ? "阳" : "阴")
          .append("年").append(isMale ? "男" : "女").append("\n");
        if (qiYunAge <= 0) qiYunAge = 1;
        sb.append("起运：").append(qiYunAge).append("岁\n");

        int step = forward ? 1 : -1;
        for (int i = 0; i < 8; i++) {
            int age = qiYunAge + i * 10;
            int dStemIdx = (mStem + step * i) % 10;
            int dBranchIdx = (mBranch + step * i) % 12;
            if (dStemIdx < 0) dStemIdx += 10;
            if (dBranchIdx < 0) dBranchIdx += 12;
            sb.append(age).append("-").append(age + 9).append("岁  ")
              .append(STEMS[dStemIdx]).append(BRANCHES[dBranchIdx]).append("\n");
        }
        return sb.toString();
    }

    private int[][] calcPillars(int year, int month, int day, int hour) {
        boolean isLateZi = (hour == 23);
        LocalDate date = LocalDate.of(year, month, day);
        if (isLateZi) date = date.plusDays(1);

        int yStem = (year - 4) % 10;
        int yBranch = (year - 4) % 12;
        if (yStem < 0) yStem += 10;
        if (yBranch < 0) yBranch += 12;

        int mBranch = month % 12;
        int mStem = (yStem % 5 * 2 + mBranch) % 10;
        if (mStem < 0) mStem += 10;

        int[] dayPillar = calculateDayPillar(date);
        int dStem = dayPillar[0];
        int dBranch = dayPillar[1];

        int hBranch = HOUR_BRANCH[hour];
        int hStem = (dStem % 5 * 2 + hBranch) % 10;

        return new int[][]{
            {yStem, mStem, dStem, hStem},
            {yBranch, mBranch, dBranch, hBranch}
        };
    }

    private int[] calculateDayPillar(LocalDate date) {
        int y = date.getYear() % 100;
        int base = (y + 7) * 5 + 15 + (y + 19) / 4;
        base %= 60;
        int total = (base + date.getDayOfYear() - 1) % 60;
        return new int[]{total % 10, total % 12};
    }

    private String calcShiShen(int dayStem, int targetStem) {
        int dElem = dayStem / 2;
        int tElem = targetStem / 2;
        boolean sameYy = (dayStem % 2) == (targetStem % 2);

        if (tElem == dElem) return sameYy ? SHI_SHEN[0] : SHI_SHEN[1];
        if ((dElem + 1) % 5 == tElem) return sameYy ? SHI_SHEN[2] : SHI_SHEN[3];
        if ((dElem + 2) % 5 == tElem) return sameYy ? SHI_SHEN[4] : SHI_SHEN[5];
        if ((dElem + 3) % 5 == tElem) return sameYy ? SHI_SHEN[6] : SHI_SHEN[7];
        return sameYy ? SHI_SHEN[8] : SHI_SHEN[9];
    }

    private int getNayinIndex(int stem, int branch) {
        int diff = ((branch - stem) % 12 + 12) % 12;
        int[] k = {0, 5, 4, 3, 2, 1};
        return k[diff / 2] * 10 + stem;
    }

    public String calculateAnnualFortune(int year, int month, int day, int hour, int minute, String gender) {
        try {
            int[][] p = calcPillars(year, month, day, hour);
            int[] stems = p[0];
            int[] branches = p[1];

            LocalDate now = LocalDate.now();
            int currYear = now.getYear();
            int yStem = (currYear - 4) % 10; if (yStem < 0) yStem += 10;
            int yBranch = (currYear - 4) % 12; if (yBranch < 0) yBranch += 12;

            String[] labels = {"年柱", "月柱", "日柱", "时柱"};
            StringBuilder sb = new StringBuilder();
            sb.append("【").append(currYear).append("年流年运势】\n\n");
            sb.append("当前流年：").append(STEMS[yStem]).append(BRANCHES[yBranch]).append("年\n");
            sb.append("您的年龄：").append(currYear - year).append("岁\n");

            if (gender != null && !gender.isEmpty()) {
                int age = currYear - year;
                int qiYun = calcQiYunAge(year, month, day, stems[0], gender);
                if (qiYun > 0) {
                    boolean isYang = stems[0] % 2 == 0;
                    boolean isMale = "男".equals(gender);
                    boolean forward = (isYang && isMale) || (!isYang && !isMale);
                    int step = forward ? 1 : -1;
                    int idx = (age - qiYun) / 10;
                    if (idx >= 0 && idx < 8) {
                        int dStemIdx = (stems[1] + step * idx) % 10; if (dStemIdx < 0) dStemIdx += 10;
                        int dBranchIdx = (branches[1] + step * idx) % 12; if (dBranchIdx < 0) dBranchIdx += 12;
                        int startAge = qiYun + idx * 10;
                        sb.append("当前大运：").append(startAge).append("-").append(startAge + 9).append("岁 ")
                          .append(STEMS[dStemIdx]).append(BRANCHES[dBranchIdx]).append("运\n");
                    }
                }
            }

            sb.append("\n流年与原局互动：\n");
            int[] chong = {6, 7, 8, 9, 10, 11, 0, 1, 2, 3, 4, 5};
            int[] liuHe = {1, 0, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2};
            int[] liuHai = {7, 6, 5, 4, -1, -1, 1, 0, 11, 10, 9, 8};
            int[] sanHeGroup = {0, 3, 2, 1, 0, 3, 2, 1, 0, 3, 2, 1};

            for (int i = 0; i < 4; i++) {
                sb.append(labels[i]).append(STEMS[stems[i]]).append(BRANCHES[branches[i]]).append(" → ");
                List<String> parts = new ArrayList<>();

                if (Math.abs(stems[i] - yStem) == 5) parts.add("天干五合");
                else if (stems[i] == yStem) parts.add("天干伏吟");
                else {
                    int elem1 = stems[i] / 2, elem2 = yStem / 2;
                    if (elem1 == elem2) parts.add("天干同五行");
                    else if ((elem2 + 1) % 5 == elem1) parts.add("天干生我");
                    else if ((elem2 + 4) % 5 == elem1) parts.add("天干我生");
                    else parts.add("天干相克");
                }

                int cb = branches[i];
                if (cb == yBranch) parts.add("地支伏吟");
                else if (chong[cb] == yBranch) parts.add("六冲");
                else if (liuHe[cb] == yBranch) parts.add("六合");
                else if (liuHai[cb] == yBranch) parts.add("六害");
                else if (sanHeGroup[cb] == sanHeGroup[yBranch]) parts.add("三合半合");

                sb.append(String.join("，", parts)).append("\n");
            }

            sb.append("\n— 以上内容由算法推算，仅供娱乐参考 —");
            return sb.toString();
        } catch (Exception e) {
            log.warn("流年运势计算失败", e);
            return "流年运势推算失败，请检查日期是否有效";
        }
    }

    public String calculateElementPreference(int year, int month, int day, int hour, int minute) {
        try {
            int[][] p = calcPillars(year, month, day, hour);
            int[] stems = p[0];
            int[] branches = p[1];
            int dStem = stems[2];

            int dElem = dStem / 2;
            int mBranch = branches[1];
            int[] monthWang = {4, 2, 0, 0, 2, 1, 1, 2, 3, 3, 2, 4};
            int mWang = monthWang[mBranch];

            int[] elemCount = new int[5];
            for (int i = 0; i < 4; i++) {
                elemCount[stems[i] / 2]++;
                for (int h : BRANCH_HIDDEN_STEMS[branches[i]]) {
                    elemCount[h / 2]++;
                }
            }

            boolean deLing = (dElem == mWang);
            boolean deXiang = ((dElem + 4) % 5 == mWang);
            boolean hasRoot = false;
            for (int i = 0; i < 4; i++) {
                for (int h : BRANCH_HIDDEN_STEMS[branches[i]]) {
                    if (h / 2 == dElem) { hasRoot = true; break; }
                }
                if (hasRoot) break;
            }

            String strength;
            if (deLing && (hasRoot || elemCount[dElem] >= 3)) strength = "身强";
            else if (deLing || hasRoot || elemCount[dElem] >= 3) strength = "中和偏强";
            else if (elemCount[dElem] <= 1 && !hasRoot) strength = "身弱";
            else strength = "中和偏弱";

            String[] elemNames = {"木", "火", "土", "金", "水"};
            List<String> yongs = new ArrayList<>();
            List<String> jis = new ArrayList<>();
            if (strength.contains("强")) {
                yongs.add(elemNames[(dElem + 3) % 5]);
                yongs.add(elemNames[(dElem + 2) % 5]);
                yongs.add(elemNames[(dElem + 1) % 5]);
                jis.add(elemNames[dElem]);
                jis.add(elemNames[(dElem + 4) % 5]);
            } else {
                yongs.add(elemNames[dElem]);
                yongs.add(elemNames[(dElem + 4) % 5]);
                jis.add(elemNames[(dElem + 3) % 5]);
                jis.add(elemNames[(dElem + 2) % 5]);
                jis.add(elemNames[(dElem + 1) % 5]);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【五行喜忌分析】\n\n");
            sb.append("日主：").append(STEMS[dStem]).append(elemNames[dElem]).append("\n");
            sb.append("生于：").append(BRANCHES[mBranch]).append("月（").append(elemNames[mWang]).append("当令）\n\n");

            sb.append("五行分布（含藏干）：\n");
            sb.append("木 ").append(elemCount[0]).append("  火 ").append(elemCount[1])
              .append("  土 ").append(elemCount[2]).append("  金 ").append(elemCount[3])
              .append("  水 ").append(elemCount[4]).append("\n\n");

            sb.append("日主状态：").append(strength).append("\n");
            if (deLing) sb.append("· 得令（值月令，当旺）\n");
            else if (deXiang) sb.append("· 得相（得月令相生）\n");
            else sb.append("· 失令（休囚无气）\n");
            sb.append(hasRoot ? "· 有根（地支通根）\n\n" : "· 无根（地支无根）\n\n");

            sb.append("喜用神：").append(String.join("、", yongs)).append("\n");
            sb.append("忌神：").append(String.join("、", jis)).append("\n\n");

            sb.append("【神煞】\n").append(calcShenSha(stems, branches)).append("\n");
            sb.append("【格局】\n").append(calcGeJu(dStem, stems, branches)).append("\n");

            sb.append("— 以上内容由算法推算，仅供娱乐参考 —");
            return sb.toString();
        } catch (Exception e) {
            log.warn("五行喜忌计算失败", e);
            return "五行喜忌推算失败，请检查日期是否有效";
        }
    }

    private int calcQiYunAge(int year, int month, int day, int yStem, String gender) {
        if (gender == null || gender.isEmpty()) return -1;
        try {
            boolean isYang = yStem % 2 == 0;
            boolean isMale = "男".equals(gender);
            boolean forward = (isYang && isMale) || (!isYang && !isMale);
            Solar birth = Solar.fromYmd(year, month, day);
            Lunar lunar = birth.getLunar();
            JieQi jie = forward ? lunar.getNextJie() : lunar.getPrevJie();
            Solar jieSolar = jie.getSolar();
            int daysDiff = Math.abs(jieSolar.subtract(birth));
            int qiYunAge = daysDiff / 3;
            return qiYunAge <= 0 ? 1 : qiYunAge;
        } catch (Exception e) {
            return -1;
        }
    }

    private int calcShiShenIndex(int dayStem, int targetStem) {
        int dElem = dayStem / 2;
        int tElem = targetStem / 2;
        boolean sameYy = (dayStem % 2) == (targetStem % 2);
        if (tElem == dElem) return sameYy ? 0 : 1;
        if ((dElem + 1) % 5 == tElem) return sameYy ? 2 : 3;
        if ((dElem + 2) % 5 == tElem) return sameYy ? 4 : 5;
        if ((dElem + 3) % 5 == tElem) return sameYy ? 6 : 7;
        return sameYy ? 8 : 9;
    }

    private String calcShenSha(int[] stems, int[] branches) {
        int dStem = stems[2];
        int yearBranch = branches[0];
        String[] labels = {"年柱", "月柱", "日柱", "时柱"};
        String[] branchNames = BRANCHES;
        String[] stemNames = STEMS;

        int[][] TIAN_YI = {
            {1, 7}, {0, 8}, {11, 9}, {11, 9}, {1, 7},
            {0, 8}, {1, 7}, {2, 6}, {3, 5}, {3, 5}
        };
        int[] WEN_CHANG = {5, 6, 8, 9, 8, 9, 11, 0, 2, 3};
        int[] LU_SHEN = {2, 3, 5, 6, 5, 6, 8, 9, 11, 0};
        int[] SAN_HE_GROUP = {0, 3, 2, 1, 0, 3, 2, 1, 0, 3, 2, 1};
        int[] YI_MA = {2, 5, 8, 11};
        int[] TAO_HUA = {9, 0, 3, 6};
        int[] HUA_GAI = {4, 7, 10, 1};
        int[] SEASON_G = {0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 0};
        int[] GU_CHEN = {2, 5, 8, 11};
        int[] GUA_SU = {10, 1, 4, 7};

        List<String> found = new ArrayList<>();
        List<String> detailLines = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            int b = branches[i];
            for (int t : TIAN_YI[dStem]) {
                if (b == t && !found.contains("天乙贵人")) {
                    found.add("天乙贵人");
                    detailLines.add("天乙贵人（" + labels[i] + branchNames[b] + "，逢凶化吉遇贵人）");
                    break;
                }
            }
            if (b == WEN_CHANG[dStem] && !found.contains("文昌贵人")) {
                found.add("文昌贵人");
                detailLines.add("文昌贵人（" + labels[i] + branchNames[b] + "，聪明好学文采出众）");
            }
            if (b == LU_SHEN[dStem] && !found.contains("禄神")) {
                found.add("禄神");
                detailLines.add("禄神（" + labels[i] + branchNames[b] + "，财禄丰足衣食无忧）");
            }
        }

        for (int i = 0; i < 4; i++) {
            int b = branches[i];
            int sg = SAN_HE_GROUP[yearBranch];
            if (b == YI_MA[sg] && !found.contains("驿马")) {
                found.add("驿马");
                detailLines.add("驿马（" + labels[i] + branchNames[b] + "，一生多动宜向外发展）");
            }
            if (b == TAO_HUA[sg] && !found.contains("桃花")) {
                found.add("桃花");
                detailLines.add("桃花（" + labels[i] + branchNames[b] + "，人缘好有艺术审美）");
            }
            if (b == HUA_GAI[sg] && !found.contains("华盖")) {
                found.add("华盖");
                detailLines.add("华盖（" + labels[i] + branchNames[b] + "，聪慧孤独有宗教缘）");
            }
            int sg2 = SEASON_G[yearBranch];
            if (b == GU_CHEN[sg2] && !found.contains("孤辰")) {
                found.add("孤辰");
                detailLines.add("孤辰（" + labels[i] + branchNames[b] + "，内心独立喜独处）");
            }
            if (b == GUA_SU[sg2] && !found.contains("寡宿")) {
                found.add("寡宿");
                detailLines.add("寡宿（" + labels[i] + branchNames[b] + "，性格安静宜修身）");
            }
        }

        if (found.isEmpty()) return "八字中无明显神煞。";

        StringBuilder sb = new StringBuilder();
        sb.append("您的八字中带").append(String.join("、", detailLines)).append("。\n");

        boolean hasTY = found.contains("天乙贵人");
        boolean hasWC = found.contains("文昌贵人");
        boolean hasLU = found.contains("禄神");
        boolean hasTH = found.contains("桃花");
        boolean hasYM = found.contains("驿马");
        boolean hasHG = found.contains("华盖");
        boolean hasGC = found.contains("孤辰") || found.contains("寡宿");

        sb.append("综合来看，");
        List<String> parts = new ArrayList<>();
        if (hasTY && hasWC) parts.add("天乙配文昌，贵人运与学业运俱佳，适合文教领域");
        else if (hasTY && hasLU) parts.add("天乙配禄神，贵人相助且财禄丰足");
        else if (hasTY) parts.add("天乙贵人入命，一生贵人运强");
        if (hasWC && !hasTY) parts.add("文昌入命，聪明好学，学业运佳");
        if (hasLU && !hasTY) parts.add("禄神入命，衣食丰足");
        if (hasTH && hasYM) parts.add("桃花配驿马，人缘广阔，宜向外地发展");
        else if (hasTH && hasHG) parts.add("桃花配华盖，艺术天赋高，情感丰富");
        else if (hasTH) parts.add("桃花入命，人缘好有魅力");
        if (hasYM && !hasTH) parts.add("驿马入命，一生奔波，宜动中求财");
        if (hasHG && !hasTH && !hasYM) parts.add("华盖入命，聪慧独立，有宗教艺术缘分");
        if (hasGC) parts.add("孤辰寡宿入命，内心独立，宜静不宜闹");
        if (parts.isEmpty()) parts.add("各项神煞均衡，一生平顺");
        sb.append(String.join("；", parts)).append("。");

        return sb.toString();
    }

    private String calcGeJu(int dStem, int[] stems, int[] branches) {
        int mBranch = branches[1];
        int mainHidden = BRANCH_HIDDEN_STEMS[mBranch][0];
        int ssIdx = calcShiShenIndex(dStem, mainHidden);

        String[] geJu = {"比肩格", "劫财格", "食神格", "伤官格",
                         "偏财格", "正财格", "七杀格", "正官格", "偏印格", "正印格"};
        String[] geJuDesc = {
            "独立性强，凡事靠自己。",
            "独立性强，凡事靠自己。",
            "温和有才华，适合技术艺术类工作。",
            "聪慧灵动，宜发挥创意。",
            "财运佳，适合经商或投资。",
            "务实稳健，适合经商或财务工作。",
            "果断刚强，适合武职或竞争性行业。",
            "正直守法，适合公职或管理类工作。",
            "仁慈好学，适合教育文化类工作。",
            "仁慈善良，学业运佳，适合文教类工作。"
        };
        int[] linGuan = {2, 3, 5, 6, 5, 6, 8, 9, 11, 0};
        int[] diWang = {3, 2, 6, 5, 6, 5, 9, 8, 0, 11};
        String[] pLabels = {"年柱", "月柱", "日柱", "时柱"};

        boolean touGan = false;
        int touGanIdx = -1;
        for (int i = 0; i < 4; i++) {
            if (stems[i] == mainHidden) { touGan = true; touGanIdx = i; break; }
        }

        boolean isJianLu = (mBranch == linGuan[dStem]);
        boolean isYangRen = (dStem % 2 == 0 && mBranch == diWang[dStem]);

        StringBuilder sb = new StringBuilder();
        if (touGan && ssIdx >= 2) {
            sb.append("月令").append(STEMS[mainHidden]).append(BRANCHES[mBranch])
              .append("为日主").append(STEMS[dStem]).append("之").append(SHI_SHEN[ssIdx])
              .append("，透于").append(pLabels[touGanIdx])
              .append("，成").append(geJu[ssIdx]).append("。").append(geJuDesc[ssIdx]);
        } else if (isJianLu) {
            sb.append("月令为日主").append(STEMS[dStem]).append("之建禄，成建禄格。自力更生，不宜合伙，宜独立发展。");
        } else if (isYangRen) {
            sb.append("月令为日主").append(STEMS[dStem]).append("之羊刃，成羊刃格。性格刚强果断，竞争心强，宜武职或创业。");
        } else if (touGan && ssIdx < 2) {
            sb.append("月令").append(STEMS[mainHidden]).append(BRANCHES[mBranch])
              .append("为比劫，成月劫格。独立性强，不宜合作，喜财官透出。");
        } else {
            sb.append("月令").append(STEMS[mainHidden]).append(BRANCHES[mBranch])
              .append("不透干，格局不显，以五行喜忌综合判断为佳。");
        }

        return sb.toString();
    }

    private String buildResult(int dayStem, int[] stems, int[] branches,
                                int year, int month, int day, int hour, int minute, boolean isLateZi) {
        StringBuilder sb = new StringBuilder();

        sb.append("【四柱八字】\n\n");
        sb.append(String.format("出生时间：%04d-%02d-%02d %02d:%02d\n", year, month, day, hour, minute));
        sb.append("生肖：").append(ANIMALS[branches[0]]).append("\n\n");

        String[] labels = {"年柱", "月柱", "日柱", "时柱"};
        for (int i = 0; i < 4; i++) {
            String ss = calcShiShen(dayStem, stems[i]);
            sb.append(labels[i]).append("：").append(STEMS[stems[i]]).append(BRANCHES[branches[i]])
              .append("(").append(ss).append(")  ");

            int[] hidden = BRANCH_HIDDEN_STEMS[branches[i]];
            if (hidden.length > 1 || !(hidden.length == 1 && hidden[0] == stems[i])) {
                sb.append("藏：");
                List<String> hiddenSs = new ArrayList<>();
                for (int h : hidden) {
                    hiddenSs.add(STEMS[h] + "(" + calcShiShen(dayStem, h) + ")");
                }
                sb.append(String.join(" ", hiddenSs));
            }
            sb.append("\n");
        }

        if (isLateZi) {
            sb.append("注：23点后属晚子时，日柱按次日计算\n");
        }

        sb.append("\n【五行】\n");
        for (int i = 0; i < 4; i++) {
            sb.append(labels[i]).append(" ").append(STEM_ELEMENTS[stems[i]]).append("(").append(STEMS[stems[i]]).append(") ")
              .append(BRANCH_ELEMENTS[branches[i]]).append("(").append(BRANCHES[branches[i]]).append(")\n");
        }

        sb.append("\n【纳音】\n");
        for (int i = 0; i < 4; i++) {
            int idx = getNayinIndex(stems[i], branches[i]);
            sb.append(labels[i]).append(" ").append(NAYIN[idx / 2]).append("\n");
        }

        sb.append("\n— 以上内容由算法推算，仅供娱乐参考 —");
        return sb.toString();
    }
}
