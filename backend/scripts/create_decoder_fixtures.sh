#!/usr/bin/env bash
set -euo pipefail

ASSETS="$(cd "$(dirname "$0")/../.." && pwd)/app/src/androidTest/assets/video"
mkdir -p "$ASSETS"

ffmpeg -y \
  -f lavfi \
  -i "testsrc2=size=320x180:rate=30:duration=1" \
  -an -c:v libx264 -preset ultrafast -crf 28 \
  -pix_fmt yuv420p \
  "$ASSETS/decoder_30_base.mp4"

if ffmpeg -y \
  -display_rotation 90 \
  -i "$ASSETS/decoder_30_base.mp4" \
  -c copy \
  "$ASSETS/decoder_30_rotated.mp4"
then
  true
else
  ffmpeg -y \
    -i "$ASSETS/decoder_30_base.mp4" \
    -c copy \
    -metadata:s:v:0 rotate=90 \
    "$ASSETS/decoder_30_rotated.mp4"
fi

ffmpeg -y \
  -f lavfi \
  -i "testsrc2=size=320x180:rate=60:duration=1" \
  -an -c:v libx264 -preset ultrafast -crf 28 \
  -pix_fmt yuv420p \
  "$ASSETS/decoder_60.mp4"

rm -f "$ASSETS/decoder_30_base.mp4"

echo "===== 30 FPS rotated ====="
ffprobe -v error -select_streams v:0 \
  -show_entries stream=width,height,r_frame_rate,avg_frame_rate,nb_frames:stream_tags=rotate:stream_side_data=rotation \
  -of json "$ASSETS/decoder_30_rotated.mp4"

echo "===== 60 FPS ====="
ffprobe -v error -select_streams v:0 \
  -show_entries stream=width,height,r_frame_rate,avg_frame_rate,nb_frames \
  -of json "$ASSETS/decoder_60.mp4"

echo "VIDEO DECODER FIXTURES: CREATED"