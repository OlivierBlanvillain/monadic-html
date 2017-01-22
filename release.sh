#!/bin/sh

set -eux

# Check that the working directory is a git repository and the repository has no outstanding changes.
git diff-index --quiet HEAD

# Prints the hash of the last commit to the console.
git rev-parse HEAD

# If there are any snapshot dependencies, ask the user whether to continue or not (default: no).
releaseVersion=$(cat version.sbt | grep -Po 'version in ThisBuild := "\K.*?(?=")' | sed -e "s/-SNAPSHOT$//")
nextVersion=$(echo "$releaseVersion" | perl -pe 's/^((\d+\.)*)(\d+)(.*)$/$1.($3+1).$4/e')-SNAPSHOT

# Ask the user for the release version and the next development version. Sensible defaults are provided.
read -r -p "Publish $releaseVersion and set next version to $nextVersion? [y/N] " response
case $response in
  [yY]) ;;
  *) exit 1;;
esac

# Run test:test, if any test fails, the release process is aborted.
sbt +test

# Write version in ThisBuild := "$releaseVersion" to the file version.sbt and also apply this setting to the current build state.
echo "version in ThisBuild := \"$releaseVersion\"" > version.sbt

# Commit the changes in version.sbt.
git commit -am "Setting version to $releaseVersion"

# Tag the previous commit with v$version (eg. v1.2, v1.2.3).
git tag "v$releaseVersion"

# Run publish.
sbt +publish-signed

# Write version in ThisBuild := "nextVersion" to the file version.sbt and also apply this setting to the current build state.
echo "version in ThisBuild := \"$nextVersion\"" > version.sbt

# Commit the changes in version.sbt.
git commit -am "Setting version to $nextVersion"

# Close and promote all staging repositories
sbt sonatypeReleaseAll

# Push changes to the remote git repository
git push
git push --tags

exit 0
