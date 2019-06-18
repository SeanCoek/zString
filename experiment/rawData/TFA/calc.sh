#! /bin/bash

list=`cat $1`
data=()
i=0

for var in $list:
  do
    ${$data[$i]}=$var;
    i++;
  done
echo ${#data[@]}

$rt
$rl
$rf
$tr
$tm
$no
$nm
for((i=0; i<${#list[@]}; i+=7))
  do
    rt+=${list[i]};
    echo $i;
    rl+=${list[i]};
    echo $i;
    rf+=${list[i]};
  done

echo $rt
echo $rl
echo $rf
