#!/system/bin/sh
#
# K6 PGO 학습 부하 — 폰(adb shell)에서 계측 빌드를 돌려 .profraw 를 모은다.
#
#   1. PGO_MODE=generate bash tools/build_engine_android.sh  -> build-{neon,dotprod}/libengine.so (계측)
#   2. adb push 계측 빌드 /data/local/tmp/rapfi-bench/engine_pgogen
#      adb push tools/pgo/train.sh /data/local/tmp/rapfi-bench/
#      adb push tools/pgo/pos_*.txt /data/local/tmp/rapfi-bench/
#      (같은 폴더에 config.toml · 가중치 4종 · model210901.bin — 빌드 산출물 bench/ 그대로)
#   3. adb shell 'cd /data/local/tmp/rapfi-bench && sh train.sh engine_pgogen'
#   4. adb pull /data/local/tmp/rapfi-bench/pgo/ && llvm-profdata merge -o rapfi-<neon|dotprod>.profdata pgo/*.profraw
#
# 학습 국면은 D4 의 다섯(개시 둘 · 중반 · VCT · VCF)이고 BENCH 국면은 쓰지 않는다 — BENCH 는 결과 불변
# 관문이자 속도 측정이라, 같은 국면으로 학습하면 이득이 부풀려진다. 렌주(4) · 표준(1) · 자유(0) 규칙을
# 모두 돌려 세 가중치 경로를 다 지난다. 2 스레드는 Lazy SMP 경로까지 프로필에 넣기 위해서다.
#
cd /data/local/tmp/rapfi-bench
EXE=./${1:-engine_pgogen}
rm -rf pgo; mkdir -p pgo
export LLVM_PROFILE_FILE=/data/local/tmp/rapfi-bench/pgo/%p.profraw
for rule in 4 1 0; do
  {
    printf 'START 15\nINFO rule %s\nINFO thread_num 2\nINFO hash_size 131072\nINFO max_memory 0\nINFO timeout_match 100000000\nINFO time_left 100000000\nINFO timeout_turn 6000\n' $rule
    for pos in open-direct open-indirect midgame vct vcf; do
      # 탐색 중에는 엔진이 다음 명령을 버리므로 수당 시간(6초)보다 길게 쉰다.
      echo YXHASHCLEAR; cat pos_$pos.txt; sleep 7
    done
    # END 로 정상 종료해야 프로필이 파일로 쓰인다(죽이면 남지 않는다).
    echo END
  } | $EXE > pgo/out_$rule.txt 2>&1
done
ls -la pgo
