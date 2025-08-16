if [ "$#" -ne 2 ]; then
    echo "Error: two arguments required."
    echo "Example: ./commit_version.sh \"提交说明\" v0.1.1"
    exit 1
fi

COMMIT_MSG=$1
VERSION_TAG=$2
VERSION_NO_PREFIX=${VERSION_TAG#v}

MOD_VERSION=$(grep "^mod_version=" gradle.properties | cut -d'=' -f2 | tr -d '\r')

if [ -z "$MOD_VERSION" ]; then
    echo "Error: 'mod_version' not found in gradle.properties"
    exit 1
fi

if [ "$MOD_VERSION" != "$VERSION_NO_PREFIX" ]; then
    echo "Error: Tag version mismatch!"
    echo "gradle.properties 里的 mod_version 是 '$MOD_VERSION'"
    echo "你输入的 tag 是 '$VERSION_TAG'（去掉前缀为 '$VERSION_NO_PREFIX'）"
    exit 1
fi

echo "开始提交：$COMMIT_MSG"
echo "版本号标签：$VERSION_TAG"
git add .
git commit -m "$COMMIT_MSG"
git tag -a $VERSION_TAG -m "$COMMIT_MSG"

git push origin main
git push origin $VERSION_TAG

echo "提交完成并已打上标签 $VERSION_TAG"