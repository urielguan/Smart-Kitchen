package com.xykj.scm.support;

import com.xykj.scm.entity.MaterialDailyConsumption;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 轻量树模型支持，用于波动型物料的结构化特征预测。
 */
@Component
public class PurchaseDemandForecastVolatileBoostingSupport {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    private static final int MIN_HISTORY_POINTS = 14;
    private static final int MIN_TRAINING_SAMPLES = 6;
    private static final int MAX_BOOST_ROUNDS = 12;
    private static final double LEARNING_RATE = 0.30d;
    private static final double MIN_GAIN = 1e-6d;

    public VolatileForecastResult forecast(VolatileForecastRequest request) {
        if (request == null || request.basisDate() == null || request.forecastDays() <= 0) {
            return VolatileForecastResult.empty();
        }
        List<MaterialDailyConsumption> rows = normalizeRows(request.historyRows());
        if (rows.size() < MIN_HISTORY_POINTS + request.forecastDays()) {
            return VolatileForecastResult.empty();
        }

        List<TrainingSample> trainingSamples = buildTrainingSamples(rows, request.forecastDays());
        if (trainingSamples.size() < MIN_TRAINING_SAMPLES) {
            return new VolatileForecastResult(ZERO, trainingSamples.size(), false);
        }

        BoostingModel model = train(trainingSamples);
        if (model.stumps().isEmpty()) {
            return new VolatileForecastResult(ZERO, trainingSamples.size(), false);
        }

        double[] featureVector = buildFeatureVector(
                rows,
                request.basisDate(),
                request.forecastDays(),
                request.currentInventoryQty(),
                request.inventoryTurnoverDays()
        );
        double predicted = Math.max(0d, model.predict(featureVector));
        return new VolatileForecastResult(scale(predicted), trainingSamples.size(), true);
    }

    private List<MaterialDailyConsumption> normalizeRows(List<MaterialDailyConsumption> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getStatDate() != null)
                .sorted(Comparator.comparing(MaterialDailyConsumption::getStatDate))
                .toList();
    }

    private List<TrainingSample> buildTrainingSamples(List<MaterialDailyConsumption> rows, int forecastDays) {
        List<TrainingSample> samples = new ArrayList<>();
        for (int anchor = MIN_HISTORY_POINTS; anchor + forecastDays <= rows.size(); anchor++) {
            List<MaterialDailyConsumption> historyWindow = rows.subList(Math.max(0, anchor - 30), anchor);
            if (historyWindow.size() < MIN_HISTORY_POINTS) {
                continue;
            }
            MaterialDailyConsumption previousDay = rows.get(anchor - 1);
            double[] features = buildFeatureVector(
                    historyWindow,
                    rows.get(anchor).getStatDate(),
                    forecastDays,
                    previousDay.getClosingStockQty(),
                    computeInventoryTurnoverDays(historyWindow)
            );
            double target = 0d;
            for (int i = anchor; i < anchor + forecastDays; i++) {
                target += toDouble(rows.get(i).getConsumedQty());
            }
            samples.add(new TrainingSample(features, target));
        }
        return samples;
    }

    private BoostingModel train(List<TrainingSample> samples) {
        double baseValue = samples.stream().mapToDouble(TrainingSample::target).average().orElse(0d);
        double[] predictions = new double[samples.size()];
        for (int i = 0; i < predictions.length; i++) {
            predictions[i] = baseValue;
        }
        List<DecisionStump> stumps = new ArrayList<>();
        for (int round = 0; round < MAX_BOOST_ROUNDS; round++) {
            double[] residuals = new double[samples.size()];
            for (int i = 0; i < samples.size(); i++) {
                residuals[i] = samples.get(i).target() - predictions[i];
            }
            DecisionStump bestStump = findBestStump(samples, residuals);
            if (bestStump == null || bestStump.gain() <= MIN_GAIN) {
                break;
            }
            stumps.add(bestStump);
            for (int i = 0; i < samples.size(); i++) {
                predictions[i] += LEARNING_RATE * bestStump.predict(samples.get(i).features());
            }
        }
        return new BoostingModel(baseValue, stumps);
    }

    private DecisionStump findBestStump(List<TrainingSample> samples, double[] residuals) {
        if (samples.isEmpty()) {
            return null;
        }
        int featureCount = samples.get(0).features().length;
        double totalSse = 0d;
        for (double residual : residuals) {
            totalSse += residual * residual;
        }

        DecisionStump best = null;
        for (int featureIndex = 0; featureIndex < featureCount; featureIndex++) {
            List<Double> values = new ArrayList<>();
            for (TrainingSample sample : samples) {
                values.add(sample.features()[featureIndex]);
            }
            values.sort(Double::compareTo);
            for (int idx = 1; idx < values.size(); idx++) {
                double left = values.get(idx - 1);
                double right = values.get(idx);
                if (Double.compare(left, right) == 0) {
                    continue;
                }
                double threshold = (left + right) / 2d;
                double leftSum = 0d;
                double rightSum = 0d;
                int leftCount = 0;
                int rightCount = 0;
                for (int sampleIndex = 0; sampleIndex < samples.size(); sampleIndex++) {
                    double value = samples.get(sampleIndex).features()[featureIndex];
                    if (value <= threshold) {
                        leftSum += residuals[sampleIndex];
                        leftCount++;
                    } else {
                        rightSum += residuals[sampleIndex];
                        rightCount++;
                    }
                }
                if (leftCount == 0 || rightCount == 0) {
                    continue;
                }
                double leftMean = leftSum / leftCount;
                double rightMean = rightSum / rightCount;
                double splitSse = 0d;
                for (int sampleIndex = 0; sampleIndex < samples.size(); sampleIndex++) {
                    double estimate = samples.get(sampleIndex).features()[featureIndex] <= threshold ? leftMean : rightMean;
                    double diff = residuals[sampleIndex] - estimate;
                    splitSse += diff * diff;
                }
                double gain = totalSse - splitSse;
                if (best == null || gain > best.gain()) {
                    best = new DecisionStump(featureIndex, threshold, leftMean, rightMean, gain);
                }
            }
        }
        return best;
    }

    private double[] buildFeatureVector(
            List<MaterialDailyConsumption> rows,
            LocalDate sampleDate,
            int forecastDays,
            BigDecimal currentInventoryQty,
            BigDecimal inventoryTurnoverDays
    ) {
        List<Double> series = rows.stream()
                .map(MaterialDailyConsumption::getConsumedQty)
                .map(this::toDouble)
                .toList();
        return new double[]{
                average(series, 3),
                average(series, 7),
                average(series, 14),
                average(series, 30),
                standardDeviation(series, 7),
                standardDeviation(series, 14),
                standardDeviation(series, 30),
                latest(series),
                maximum(series, 7),
                minimum(series, 7),
                activeRatio(series, 14),
                coefficientOfVariation(series, 14),
                sampleDate == null ? 0d : sampleDate.getDayOfWeek().getValue() / 7d,
                sampleDate != null && isWeekend(sampleDate) ? 1d : 0d,
                sampleDate == null ? 0d : sampleDate.getMonthValue() / 12d,
                sampleDate == null ? 0d : seasonIndex(sampleDate) / 4d,
                toDouble(currentInventoryQty),
                toDouble(inventoryTurnoverDays),
                forecastDays
        };
    }

    private BigDecimal computeInventoryTurnoverDays(List<MaterialDailyConsumption> rows) {
        if (rows == null || rows.isEmpty()) {
            return ZERO;
        }
        double averageInventory = 0d;
        for (MaterialDailyConsumption row : rows) {
            double opening = toDouble(row.getOpeningStockQty());
            double closing = toDouble(row.getClosingStockQty());
            averageInventory += (opening + closing) / 2d;
        }
        averageInventory = averageInventory / rows.size();
        double averageConsumption = rows.stream()
                .map(MaterialDailyConsumption::getConsumedQty)
                .mapToDouble(this::toDouble)
                .average()
                .orElse(0d);
        if (averageConsumption <= 0d) {
            return ZERO;
        }
        return scale(averageInventory / averageConsumption);
    }

    private boolean isWeekend(LocalDate date) {
        return date != null && (date.getDayOfWeek().getValue() == 6 || date.getDayOfWeek().getValue() == 7);
    }

    private int seasonIndex(LocalDate date) {
        if (date == null) {
            return 0;
        }
        int month = date.getMonthValue();
        if (month >= 3 && month <= 5) {
            return 1;
        }
        if (month >= 6 && month <= 8) {
            return 2;
        }
        if (month >= 9 && month <= 11) {
            return 3;
        }
        return 4;
    }

    private double average(List<Double> series, int days) {
        List<Double> window = tail(series, days);
        if (window.isEmpty()) {
            return 0d;
        }
        return window.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
    }

    private double standardDeviation(List<Double> series, int days) {
        List<Double> window = tail(series, days);
        if (window.isEmpty()) {
            return 0d;
        }
        double mean = average(series, days);
        double variance = window.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0d);
        return Math.sqrt(Math.max(variance, 0d));
    }

    private double activeRatio(List<Double> series, int days) {
        List<Double> window = tail(series, days);
        if (window.isEmpty()) {
            return 0d;
        }
        long activeCount = window.stream().filter(value -> value > 0d).count();
        return activeCount / (double) window.size();
    }

    private double coefficientOfVariation(List<Double> series, int days) {
        double avg = average(series, days);
        if (avg <= 0d) {
            return 0d;
        }
        return standardDeviation(series, days) / avg;
    }

    private double latest(List<Double> series) {
        return series.isEmpty() ? 0d : series.get(series.size() - 1);
    }

    private double maximum(List<Double> series, int days) {
        return tail(series, days).stream().mapToDouble(Double::doubleValue).max().orElse(0d);
    }

    private double minimum(List<Double> series, int days) {
        return tail(series, days).stream().mapToDouble(Double::doubleValue).min().orElse(0d);
    }

    private List<Double> tail(List<Double> series, int days) {
        if (series == null || series.isEmpty() || days <= 0) {
            return List.of();
        }
        int fromIndex = Math.max(0, series.size() - days);
        return series.subList(fromIndex, series.size());
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    public record VolatileForecastRequest(
            LocalDate basisDate,
            int forecastDays,
            List<MaterialDailyConsumption> historyRows,
            BigDecimal currentInventoryQty,
            BigDecimal inventoryTurnoverDays
    ) {
    }

    public record VolatileForecastResult(
            BigDecimal predictedQty,
            int sampleCount,
            boolean trained
    ) {
        public static VolatileForecastResult empty() {
            return new VolatileForecastResult(ZERO, 0, false);
        }
    }

    private record TrainingSample(double[] features, double target) {
    }

    private record DecisionStump(
            int featureIndex,
            double threshold,
            double leftValue,
            double rightValue,
            double gain
    ) {
        private double predict(double[] features) {
            return features[featureIndex] <= threshold ? leftValue : rightValue;
        }
    }

    private record BoostingModel(double baseValue, List<DecisionStump> stumps) {
        private double predict(double[] features) {
            double prediction = baseValue;
            for (DecisionStump stump : stumps) {
                prediction += LEARNING_RATE * stump.predict(features);
            }
            return prediction;
        }
    }
}
