#!/bin/bash
# Reconstruct and decompress large JSON datasets

# Find root of the project (parent of scripts directory)
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$(dirname "$DIR")")"
DOC_DIR="$ROOT_DIR/doc"

echo "Navigating to doc directory: $DOC_DIR"
cd "$DOC_DIR" || { echo "Failed to navigate to doc directory"; exit 1; }

echo "=== Decompressing surveyDownload.json ==="
if [ -f "surveyDownload.json.gz" ]; then
    gzip -d -c surveyDownload.json.gz > surveyDownload.json
    echo "✓ Restored surveyDownload.json"
else
    echo "✗ surveyDownload.json.gz not found!"
fi

echo "=== Decompressing FoodData_Central_sr_legacy_food_json_2018-04.json ==="
if [ -f "FoodData_Central_sr_legacy_food_json_2018-04.json.gz" ]; then
    gzip -d -c FoodData_Central_sr_legacy_food_json_2018-04.json.gz > FoodData_Central_sr_legacy_food_json_2018-04.json
    echo "✓ Restored FoodData_Central_sr_legacy_food_json_2018-04.json"
else
    echo "✗ FoodData_Central_sr_legacy_food_json_2018-04.json.gz not found!"
fi

echo "=== Reconstructing and decompressing FoodData_Central_branded_food_json_2026-04-30.json ==="
# Check if parts exist
if ls FoodData_Central_branded_food_json_2026-04-30.json.gz.part_* 1> /dev/null 2>&1; then
    cat FoodData_Central_branded_food_json_2026-04-30.json.gz.part_* | gzip -d -c > FoodData_Central_branded_food_json_2026-04-30.json
    echo "✓ Restored FoodData_Central_branded_food_json_2026-04-30.json"
else
    echo "✗ FoodData_Central_branded_food_json_2026-04-30.json.gz parts not found!"
fi

echo "All dataset restorations complete!"
