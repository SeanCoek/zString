#! /bin/bash

cd "(dirname "$0")"

#eclipse
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms256m -jar zstring.jar -pp /home/sean/bench_compared/eclipse.jar -split -d Test.txt
done
