#! /bin/bash

java -cp lib/fileutils.jar:. spec.benchmarks.scimark.fft.Main
java -cp lib/fileutils.jar:. spec.benchmarks.scimark.lu.Main
java -cp lib/fileutils.jar:. spec.benchmarks.scimark.monte_carlo.Main
java -cp lib/fileutils.jar:. spec.benchmarks.scimark.sor.Main
java -cp lib/fileutils.jar:. spec.benchmarks.scimark.sparse.Main
