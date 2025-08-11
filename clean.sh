#!/bin/bash
# sometimes android studio builds old apk despite having latest codes. We need to clear the caches
# to repair this issue
echo "Cleaning previous builds and caches..."
./gradlew clean

echo "Cleaning up ~/.gradle"
rm -rf ~/.gradle

# remove build folder if needed
echo "Force deleting build folders..."
rm -rf ./build
rm -rf ./**/build

echo "Force deleting .kotlin folder..."
rm -rf ./.kotlin
echo "Force deleting .gradle folder..."
rm -rf ./.gradle
rm -rf ./**/.gradle

