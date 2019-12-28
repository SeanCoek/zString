#! /bin/bash

java -cp lib/:/home/sean/sootlib/fileutils.jar:/home/sean/sootlib/commons-io.jar:. spec.benchmarks.crypto.aes.Main
java -cp lib/:/home/sean/sootlib/fileutils.jar:/home/sean/sootlib/commons-io.jar:. spec.benchmarks.crypto.rsa.Main
java -cp lib/:/home/sean/sootlib/fileutils.jar:/home/sean/sootlib/commons-io.jar:. spec.benchmarks.crypto.signverify.Main
