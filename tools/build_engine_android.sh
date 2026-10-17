#!/usr/bin/env bash
#
# Rapfi -> arm64-v8a 온디바이스 엔진 빌드 (RapfiDroid E0).
#
# 이 스크립트가 만드는 것:
#   app/src/main/jniLibs/arm64-v8a/libengine.so          (탑재본, 기본 NEON — 모든 arm64)
#   app/src/main/jniLibs/arm64-v8a/libengine_dotprod.so  (탑재본, ARMv8.2+ — 앱이 런타임에 고른다)
#   app/src/main/assets/engine/*                  (가중치 4종 + 클래식 모델)
#   bench/                                        (폰에서 BENCH 대조할 묶음)
#
# 리눅스 x86_64 빌드 머신에서 통째로 실행한다. 이 저장소에 두는 이유는
# jniLibs/ENGINE_VERSION.txt 가 "이 바이너리가 어떻게 나왔는가"를 여기로
# 가리키기 때문이다 — 커밋된 바이너리의 출처가 없으면 재현할 수 없다.
#
set -euo pipefail

# 소스 고정. upstream HEAD 를 받으면 같은 스크립트가 날마다 다른 엔진을 만들고, 탑재본의
# BENCH 해시(e240ab16)와 RapfiWeb 의 패치 기준(3c94c2a9)이 말없이 어긋난다. 올릴 때는 이 두 줄을
# 바꾸고 [6/7] · [7/7] 의 해시를 새로 확인한다(docs/엔진-고도화-마스터플랜.md).
RAPFI_COMMIT="3c94c2a976f24a0dd1c5517623e9ab6fffe66bd7"     # dhbloo/rapfi
NETWORKS_COMMIT="e32ad77a5364363b3e3a02b3f9e8610ade19ea98"  # dhbloo/rapfi-networks (Networks 서브모듈)

# 성능 패치 — tools/patches/ 아래, 적는 순서대로 [4/7] 의 안드로이드 패치 뒤에 git apply 한다.
# 모두 결과 불변(BENCH e240ab16 유지)이어야 하고, 하나라도 안 맞으면 [4/7] 에서 멈춘다.
#   k1-neon-vqdmulh.patch : 누산기 dwconv 의 mulhi(w, 4f) 를 NEON 에서 sqdmulh(w, 2f) 1명령으로 (비트 동일)
#   k2-neon-starblock-multi.patch : 값 헤드 starBlock 의 가중치를 입력 4개에 재사용 (RapfiWeb A3 를 NEON DotProd 로, 비트 동일)
RAPFI_PATCHES="k1-neon-vqdmulh.patch k2-neon-starblock-multi.patch"
PATCH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/patches"

echo ">>> [1/7] 시스템 도구"
sudo apt-get update -qq
sudo apt-get install -y unzip ccache file
pip install -q --upgrade pip cmake ninja

echo ">>> [2/7] Android NDK r27c"
cd "$HOME"
if [ ! -d "$HOME/android-ndk-r27c" ]; then
  wget -q --show-progress https://dl.google.com/android/repository/android-ndk-r27c-linux.zip
  unzip -q android-ndk-r27c-linux.zip && rm -f android-ndk-r27c-linux.zip
fi
export ANDROID_NDK="$HOME/android-ndk-r27c"
export NDK_BIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

echo ">>> [3/7] Rapfi 소스 + 가중치(40MB)"
cd "$HOME"
rm -rf rapfi
git clone https://github.com/dhbloo/rapfi.git
cd rapfi
git checkout -q -b android "$RAPFI_COMMIT"
# 패치하기 **전** 커밋을 잡아 둔다. 아래에서 로컬 패치 커밋을 만든 뒤 rev-parse HEAD 를
# 찍으면 그건 dhbloo/rapfi 에 없는 커밋이라, ENGINE_VERSION.txt 의 upstream 필드가
# 사실과 달라진다(RapfiWeb W0 스파이크에서 이 값으로 클론하려다 발견했다).
UPSTREAM_COMMIT="$(git rev-parse HEAD)"
[ "$UPSTREAM_COMMIT" = "$RAPFI_COMMIT" ] || { echo "rapfi 가 $RAPFI_COMMIT 이 아니다: $UPSTREAM_COMMIT"; exit 1; }
git submodule update --init --depth 1 Networks
NETWORKS_HEAD="$(git -C Networks rev-parse HEAD)"
[ "$NETWORKS_HEAD" = "$NETWORKS_COMMIT" ] || {
  echo "Networks 가 $NETWORKS_COMMIT 이 아니다: $NETWORKS_HEAD (rapfi 커밋의 서브모듈 포인터가 바뀌었다)"; exit 1; }

echo ">>> [4/7] Android 호환 패치 (실패하면 여기서 멈춘다)"
python3 - <<'PY'
import pathlib, sys
p = pathlib.Path("Rapfi/CMakeLists.txt")
s = p.read_text(encoding="utf-8")

# B4: 안드로이드는 nativeLibraryDir 로 추출된 lib*.so 만 exec 할 수 있다.
a1 = 'set_target_properties(rapfi PROPERTIES OUTPUT_NAME "pbrain-rapfi")'
if a1 not in s:
    sys.exit("PATCH FAILED: anchor 1 not found - upstream CMakeLists 가 바뀌었습니다")
s = s.replace(a1, a1 + '''

# Android only executes files the package manager extracted into the app's
# nativeLibraryDir, and it only extracts entries named lib*.so. Ship the gomocup
# executable under that name; it is still an ordinary PIE executable.
if(ANDROID)
    set_target_properties(rapfi PROPERTIES
        OUTPUT_NAME "engine" PREFIX "lib" SUFFIX ".so")
endif()''', 1)

# B1: bionic 은 pthread 를 libc 안에 넣어 두어 libpthread 자체가 없다.
a2 = 'elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin" AND CMAKE_CXX_COMPILER_ID MATCHES "Clang|AppleClang")'
if a2 not in s:
    sys.exit("PATCH FAILED: anchor 2 not found - upstream CMakeLists 가 바뀌었습니다")
s = s.replace(a2, 'elseif(ANDROID)\n'
                  '            # Bionic implements pthread inside libc: there is no libpthread\n'
                  '            # to link against, and asking for one fails the link outright.\n'
                  '            target_link_libraries(rapfi PRIVATE atomic)\n'
                  '        ' + a2, 1)

p.write_text(s, encoding="utf-8")
print("OK: 2 patches applied")
PY
git commit -aqm "build: Android(arm64-v8a) 크로스 컴파일 지원

- bionic 은 pthread 를 libc 에 넣어 두어 libpthread 가 없다 -> Android 는 atomic 만 링크
- Android 는 nativeLibraryDir 의 lib*.so 만 exec 할 수 있다 -> 출력 이름을 libengine.so 로"

for patch in $RAPFI_PATCHES; do
  git apply --check "$PATCH_DIR/$patch" || { echo "PATCH FAILED: $patch"; exit 1; }
  git apply "$PATCH_DIR/$patch"
  git commit -aqm "perf: $patch"
  echo "OK: $patch"
done

echo ">>> [5/7] 빌드 (NEON / DotProd)"
cd "$HOME/rapfi/Rapfi"
build_target () {
  local name=$1 dotprod=$2
  echo "--- build: $name (DotProd=$dotprod, LTO=ON) ---"
  cmake -S . -B "build-$name" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DCMAKE_C_COMPILER_LAUNCHER=ccache \
    -DCMAKE_CXX_COMPILER_LAUNCHER=ccache \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_static \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
    -DCMAKE_BUILD_TYPE=Release \
    -DUSE_NEON=ON -DUSE_NEON_DOTPROD="$dotprod" \
    -DNO_COMMAND_MODULES=ON \
    -DENABLE_LTO=ON
  cmake --build "build-$name" -j"$(nproc)"
  "$NDK_BIN/llvm-strip" --strip-all "build-$name/libengine.so"
}
build_target neon    OFF   # 전 arm64 기기 안전 - 1차 탑재본
build_target dotprod ON    # ARMv8.2+ 전용 - 구형 기기에서는 SIGILL, 런타임 선택 필요

echo ">>> [6/7] 검증 게이트 - 조용한 저성능 빌드를 여기서 잡는다"
# 두 옵션 다 조용히 무시되는 경로가 있다: 크로스 컴파일이면 SIMD 자동감지가
# 꺼지고, ENABLE_LTO=ON 은 check_ipo_supported 실패 시 경고만 찍고 넘어간다.
# 둘 다 "빌드 성공, 바이너리는 느림"으로 끝나므로 여기서 실패시킨다.
verify () {
  local name=$1 march=$2 so="build-$1/libengine.so" nj="build-$1/build.ninja" fail=0
  echo "--- verify: $name ---"
  if ! file "$so" | grep -q "ARM aarch64"; then
    echo "  X aarch64 바이너리가 아님: $(file -b "$so")"; fail=1
  fi
  if "$NDK_BIN/llvm-readelf" -d "$so" | grep -q "libc++_shared"; then
    echo "  X libc++_shared 의존 - ANDROID_STL=c++_static 이 안 먹었다"; fail=1
  fi
  if ! grep -q "^USE_NEON:BOOL=ON" "build-$name/CMakeCache.txt"; then
    echo "  X USE_NEON 이 OFF - 스칼라(simde) 빌드다"; fail=1
  fi
  if ! grep -q -- "$march" "$nj"; then
    echo "  X $march 플래그가 컴파일 명령에 없다"; fail=1
  fi
  if ! grep -q -- "-flto" "$nj"; then
    echo "  X LTO 미적용 - check_ipo_supported 가 실패했는데 경고만 찍고 넘어갔다"; fail=1
  fi
  if ! grep -q -- "-ffp-contract=off" "$nj"; then
    echo "  X -ffp-contract=off 없음 - 서버와 탐색 결과가 갈릴 수 있다"; fail=1
  fi
  local sdot
  sdot=$("$NDK_BIN/llvm-objdump" -d "$so" | grep -cE '[[:space:]]sdot[[:space:]]' || true)
  echo "  . sdot 명령 수: $sdot   (neon=0 정상 / dotprod>0 기대)"
  echo "  . 크기: $(stat -c%s "$so") bytes"
  "$NDK_BIN/llvm-readelf" -d "$so" | grep NEEDED | sed 's/^/  . /'
  if [ "$fail" -ne 0 ]; then echo "검증 실패: $name"; exit 1; fi
  echo "  OK $name"
}
verify neon    "armv8-a+simd"
verify dotprod "armv8.2-a+dotprod"

echo ">>> [7/7] 호스트 기준선(bench) + 패키징"
cd "$HOME/rapfi/Rapfi"
cmake -S . -B build-host -G Ninja -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache \
      -DNO_COMMAND_MODULES=ON -DENABLE_LTO=ON
cmake --build build-host -j"$(nproc)"

# 엔진은 cwd 에서 config.toml 과 가중치를 찾는다(--config 인자가 없는 빌드).
# 이 config 는 default_thread_num = 1 이라 bench 가 결정적이다 - 폰과 대조하려면
# 스레드 수를 건드리지 말 것.
mkdir -p "$HOME/rapfi-run" && cd "$HOME/rapfi-run"
cp "$HOME/rapfi/Networks/config-example/config.toml" .
cp "$HOME/rapfi/Networks/mix9svq/"*.bin.lz4 .
cp "$HOME/rapfi/Networks/classical/model210901.bin" .
printf 'BENCH\nEND\n' | "$HOME/rapfi/Rapfi/build-host/pbrain-rapfi" > bench-host.txt 2>&1 || true
grep -E "Total Time|Nodes|Nodes/s|Hash:" bench-host.txt | sed 's/^/  host | /'
HOST_HASH=$(grep -oE "Hash: [0-9a-f]+" bench-host.txt | tail -1 || echo "Hash: ?")

OUT="$HOME/rapfidroid-engine"
rm -rf "$OUT"; mkdir -p "$OUT/jniLibs/arm64-v8a" "$OUT/assets/engine" "$OUT/bench"

cp "$HOME/rapfi/Rapfi/build-neon/libengine.so"    "$OUT/jniLibs/arm64-v8a/libengine.so"
# dotprod 판도 탑재한다(R1). 패키지 관리자는 lib*.so 만 추출하므로 이 이름이어야 하고, 앱의
# LocalEngineInstaller 가 asimddp 를 보고 고른다 — 못 쓰는 기기에서는 SIGILL 뒤 NEON 으로 물러난다.
cp "$HOME/rapfi/Rapfi/build-dotprod/libengine.so" "$OUT/jniLibs/arm64-v8a/libengine_dotprod.so"

# 앱 에셋: 가중치 4종 + 서버와 같은 클래식 모델
cp "$HOME/rapfi/Networks/mix9svq/"*.bin.lz4         "$OUT/assets/engine/"
cp "$HOME/rapfi/Networks/classical/model220723.bin" "$OUT/assets/engine/"

# 폰 검증 묶음 - 호스트와 '같은' config/가중치여야 대조가 성립한다
cp "$HOME/rapfi-run/config.toml" "$HOME/rapfi-run/"*.bin.lz4 \
   "$HOME/rapfi-run/model210901.bin" "$HOME/rapfi-run/bench-host.txt" "$OUT/bench/"
cp "$HOME/rapfi/Rapfi/build-neon/libengine.so"    "$OUT/bench/libengine.so"
cp "$HOME/rapfi/Rapfi/build-dotprod/libengine.so" "$OUT/bench/libengine_dotprod.so"

cat > "$OUT/jniLibs/ENGINE_VERSION.txt" <<EOF
engine     : Rapfi on-device (arm64-v8a)
upstream   : $UPSTREAM_COMMIT  (dhbloo/rapfi, 패치 전)
patched    : $(git -C "$HOME/rapfi" rev-parse HEAD)  (= 위 커밋 + 안드로이드 빌드 패치 + RAPFI_PATCHES. 이 저장소 밖에는 없다)
networks   : $(git -C "$HOME/rapfi/Networks" rev-parse HEAD)
ndk        : r27c
abi        : arm64-v8a / android-26 / c++_static / 16KB-page ready
cmake opts : -DUSE_NEON=ON -DUSE_NEON_DOTPROD=OFF|ON -DNO_COMMAND_MODULES=ON -DENABLE_LTO=ON -DCMAKE_BUILD_TYPE=Release
binaries   : libengine.so (NEON, armv8-a+simd) / libengine_dotprod.so (armv8.2-a+dotprod)
size       : $(stat -c%s "$OUT/jniLibs/arm64-v8a/libengine.so") / $(stat -c%s "$OUT/jniLibs/arm64-v8a/libengine_dotprod.so") bytes (stripped)
built_at   : $(date -u +%Y-%m-%dT%H:%M:%SZ)
bench(host): $HOST_HASH  <- 폰의 BENCH Hash 가 이 값과 같아야 한다
patches    : ${RAPFI_PATCHES:-없음}  (tools/patches/)
build_by   : RapfiDroid/tools/build_engine_android.sh
EOF
cat "$OUT/jniLibs/ENGINE_VERSION.txt"

cd "$HOME" && tar czf rapfidroid-engine.tar.gz -C "$HOME" rapfidroid-engine
echo "=========================================================="
echo "완료: $HOME/rapfidroid-engine.tar.gz  ($(du -h rapfidroid-engine.tar.gz | cut -f1))"
echo "=========================================================="
