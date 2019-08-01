#! /bin/bash

# compress
cd "(dirname "$0")"
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/compress/ -d compress.txt
done

# crypto
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/crypto/ -d crypto.txt
done

#bootstrap
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/bootstrap.jar -d bootstrap.txt
done

#commons-codec
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/codec.jar -d codec.txt
done

#junit
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/junit.jar -d junit.txt
done

#commons-httpclient
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/httpclient.jar -d httpclient.txt
done

#serializer
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/serializer.jar -d serializer.txt
done

#xerces
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/xerces.jar -d xerces.txt
done

#eclipse
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/eclipse.jar -d eclipse.txt
done

#derby
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/derby.jar -d derby.txt
done

#xalan
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/xalan.jar -d xalan.txt
done

#antlr
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/antlr.jar -d antlr.txt
done

#batik
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar chaAnalyzer.jar -pp /home/sean/bench_compared/batik.jar -d batik.txt
done





