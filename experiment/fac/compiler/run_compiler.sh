#! /bin/bash

java -cp lib/fileutils.jar:lib/javac.jar:. spec.benchmarks.compiler.compiler.Main
java -cp lib/fileutils.jar:lib/javac.jar:lib/sunflow.jar:. spec.benchmarks.compiler.sunflow.Main
