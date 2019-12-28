#! /bin/bash

java -cp lib/fileutils.jar:lib/xom-1.1.jar:lib/Tidy.jar:. spec.benchmarks.xml.transform.Main
java -cp lib/fileutils.jar:. spec.benchmarks.xml.validation.Main
