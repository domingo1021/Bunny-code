#!/bin/bash
# Runs inside LocalStack once S3 is ready.
set -euo pipefail

BUCKET=bunny-code

awslocal s3api create-bucket --bucket "$BUCKET" >/dev/null 2>&1 || true

# The web app fetches file contents straight from the bucket (as it does from CloudFront in
# production), so the browser needs CORS.
awslocal s3api put-bucket-cors --bucket "$BUCKET" --cors-configuration '{
  "CORSRules": [{
    "AllowedOrigins": ["*"],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedHeaders": ["*"],
    "MaxAgeSeconds": 3600
  }]
}'

cd /seed
find . -type f | while read -r file; do
  key="${file#./}"
  case "$key" in
    *.md)  type="text/markdown; charset=utf-8" ;;
    *.js)  type="application/javascript; charset=utf-8" ;;
    *.svg|user/user_icon.png) type="image/svg+xml" ;;
    *)     type="application/octet-stream" ;;
  esac
  awslocal s3api put-object --bucket "$BUCKET" --key "$key" --body "$file" --content-type "$type" >/dev/null
done

echo "bunny-code bucket ready with $(find . -type f | wc -l | tr -d ' ') seed objects"
