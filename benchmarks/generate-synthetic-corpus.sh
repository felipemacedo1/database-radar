#!/usr/bin/env sh
set -eu

COUNT=${1:-1000}
DESTINATION=${2:-}

case "$COUNT" in
  *[!0-9]*|'') printf '%s\n' "count must be a positive integer" >&2; exit 2 ;;
esac
if [ "$COUNT" -lt 1 ]; then
  printf '%s\n' "count must be a positive integer" >&2
  exit 2
fi
if [ -z "$DESTINATION" ]; then
  printf '%s\n' "usage: generate-synthetic-corpus.sh <count> <empty-directory>" >&2
  exit 2
fi

mkdir -p "$DESTINATION/src/main/java/benchmark"
if find "$DESTINATION/src/main/java/benchmark" -type f -name '*.java' | grep -q .; then
  printf '%s\n' "destination already contains Java files: $DESTINATION" >&2
  exit 2
fi

index=1
while [ "$index" -le "$COUNT" ]; do
  class_name=$(printf 'Synthetic%05d' "$index")
  table_number=$((index % 100))
  file="$DESTINATION/src/main/java/benchmark/$class_name.java"
  {
    printf '%s\n' 'package benchmark;'
    printf '%s\n' 'import java.sql.Connection;'
    printf '%s\n' 'import java.sql.PreparedStatement;'
    printf 'public class %s {\n' "$class_name"
    printf '%s\n' '  PreparedStatement query(Connection connection) throws Exception {'
    printf '    return connection.prepareStatement("SELECT ID, STATUS FROM TABLE_%s WHERE STATUS = ?");\n' "$table_number"
    printf '%s\n' '  }'
    printf '%s\n' '}'
  } > "$file"
  index=$((index + 1))
done

printf '%s\n' "Generated $COUNT Java files in $DESTINATION"
