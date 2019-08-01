#! /bin/bash

# compress
cd "(dirname "$0")"
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/compress/ -split -d compress.txt
done

# crypto
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/crypto/ -split -d crypto.txt
done

#bootstrap
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/bootstrap.jar -split -d bootstrap.txt
done

#commons-codec
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/codec.jar -split -d codec.txt
done

#junit
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/junit.jar -split -d junit.txt
done

#commons-httpclient
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/httpclient.jar -split -d httpclient.txt
done

#serializer
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/serializer.jar -split -d serializer.txt
done

#xerces
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/xerces.jar -split -d xerces.txt
done

#eclipse
for((i=0; i<10; i++));
do
java -jar zstring.jar -pp /home/sean/bench_compared/eclipse.jar -d eclipse.txt
done

#derby
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/derby.jar -split -d derby.txt
done

#xalan
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/xalan.jar -split -d xalan.txt
done

#antlr
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/antlr.jar -split -d antlr.txt
done

#batik
for((i=0; i<10; i++));
do
java -Xmx12288m -Xms128m -jar zstring.jar -pp /home/sean/bench_compared/batik.jar -split -d batik.txt
done





