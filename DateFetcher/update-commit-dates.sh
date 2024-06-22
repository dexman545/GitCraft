#!/bin/bash

# Usage
if [ ! "$#" -eq 1 ]; then
    echo "This script will fetch the release dates of all committed Minecraft versions and reflect those dates in the author date of the corresponding commit. This is a very dangerous script! Please pass the absolute path to the minecraft-repo as an argument! Example: ./update-commit-dates.sh ../minecraft-repo"
    exit 1
fi

DateFetcher="./build/libs/DateFetcher-0.1.0-SNAPSHOT-all.jar"

TARGET_DIR="$(cd "$(dirname "$1")"; pwd)/$(basename "$1")"
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cd $SCRIPT_DIR

# Build DateFetcher
if [ ! -f $DateFetcher ]; then
    echo "DateFetcher must be built first!"
    sh ./gradlew build
fi

# Validate repository
if [ ! -d "$TARGET_DIR/.git" ]; then
    echo "No git repository found in $TARGET_DIR!"
    ls $TARGET_DIR | echo
    exit 2
fi

read -p "You are about to perform the modifications in $TARGET_DIR. Are you sure? (y/n)" -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cd $TARGET_DIR

    git filter-repo --force --commit-callback "
        import subprocess
        import time
        from datetime import datetime, timedelta, timezone

        def getDatetime(timestamp_string):
            timestamp_string, timezone_string = timestamp_string.split()
            timestamp = int(timestamp_string)
            tz = timezone(timedelta(hours = int(timezone_string[:3]), minutes = int(timezone_string[0] + timezone_string[3:])))
            return datetime.fromtimestamp(timestamp, tz)

        mcVersion = commit.message.decode().split('\n')[0]
        print('\nVersion: ' + mcVersion)

        old_dt = getDatetime(commit.author_date.decode())
        print('From: ' + old_dt.isoformat())

        releaseTime = subprocess.run(['java', '-jar', '$SCRIPT_DIR/$DateFetcher', mcVersion, 'epoch'], capture_output=True, text=True).stdout.strip()
        new_dt = getDatetime(releaseTime)
        commit.author_date = (releaseTime).encode()
        print('To: ' + new_dt.isoformat())
    "
fi