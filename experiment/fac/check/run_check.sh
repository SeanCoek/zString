#! /bin/bash

java -cp lib/fileutils.jar:. spec.benchmarks.check.FloatingPointCheck
java -cp lib/fileutils.jar:. spec.benchmarks.check.LoopBounds
java -cp lib/fileutils.jar:. spec.benchmarks.check.LoopBounds2
#java -cp lib/fileutils.jar:. spec.benchmarks.check.PepTest
java -cp lib/fileutils.jar:. spec.benchmarks.check.syncTest
