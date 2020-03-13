#! /bin/bash

# compress
cd "(dirname "$0")"

#eclipse
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/eclipse.jar -split -d eclipse.txt
done

#derby
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/derby.jar -d derby.txt
done

#xalan
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/xalan.jar -d xalan.txt
done

#antlr
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/antlr.jar -d antlr.txt
done

#batik
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/batik.jar -d batik.txt
done





