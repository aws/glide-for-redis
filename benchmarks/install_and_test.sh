#!/bin/bash

#remove comment to print the lines running.
#set -x

set -e

if command -v python3
then
    pythonCommand=python3
else
    if command -v python
    then
        pythonCommand=python
    else
        echo "python / python3 could not be found"
        exit
    fi
fi

resultFiles=
writeResultsCSV=1
runAllBenchmarks=1
runPython=0
runNode=0
runCsharp=0
runJava=0
runGo=0
runRust=0
concurrentTasks="1 10 100 1000"
dataSize="100 4000"
clientCount="1"
chosenClients="all"
host="localhost"
port=6379
tlsFlag="--tls"
dotnetFramework="net6.0"

function runPythonBenchmark(){
  # generate protobuf files
  protoc -Iprotobuf=${GLIDE_HOME_FOLDER}/glide-core/src/protobuf/ --python_out=${PYTHON_FOLDER}/python/glide ${GLIDE_HOME_FOLDER}/glide-core/src/protobuf/*.proto
  cd ${PYTHON_FOLDER}
  $pythonCommand -m venv .env
  source .env/bin/activate
  $pythonCommand -m pip install --upgrade --quiet pip
  $pythonCommand -m pip install --quiet -r requirements.txt
  maturin develop --release
  echo "Starting Python benchmarks"
  cd ${BENCH_FOLDER}/python
  $pythonCommand -m pip install --quiet -r requirements.txt
  $pythonCommand python_benchmark.py --resultsFile=../$1 --dataSize $2 --concurrentTasks $concurrentTasks --clients $chosenClients --host $host --clientCount $clientCount $tlsFlag $clusterFlag $portFlag $minimalFlag
  # exit python virtualenv
  deactivate
  echo "done python benchmark"
}

function runNodeBenchmark(){
  cd ${BENCH_FOLDER}/../node
  npm install
  rm -rf build-ts
  npm run build:benchmark
  cd ${BENCH_FOLDER}/node
  npm install
  npx tsc
  npm run bench -- --resultsFile=../$1 --dataSize $2 --concurrentTasks $concurrentTasks --clients $chosenClients --host $host --clientCount $clientCount $tlsFlag $clusterFlag $portFlag $minimalFlag
}

function runCSharpBenchmark(){
  cd ${BENCH_FOLDER}/csharp
  dotnet clean
  dotnet build --configuration Release /warnaserror
  dotnet run --framework $dotnetFramework --configuration Release --resultsFile=../$1 --dataSize $2 --concurrentTasks $concurrentTasks --clients $chosenClients --host $host --clientCount $clientCount $tlsFlag $portFlag $minimalFlag
}

function runJavaBenchmark(){
  cd ${BENCH_FOLDER}/../java
  ./gradlew :benchmarks:run --args="-resultsFile \"${BENCH_FOLDER}/$1\" --dataSize \"$2\" --concurrentTasks \"$concurrentTasks\" --clients \"$chosenClients\" --host $host $portFlag --clientCount \"$clientCount\" $tlsFlag $clusterFlag $minimalFlag"
}

function runGoBenchmark(){
    cd ${BENCH_FOLDER}/../go/benchmarks
    export LD_LIBRARY_PATH=${BENCH_FOLDER}/../go/target/release:$LD_LIBRARY_PATH
    go run . -resultsFile ${BENCH_FOLDER}/$1 -dataSize $2 -concurrentTasks $concurrentTasks -clients $chosenClients -host $host $portFlag -clientCount $clientCount $tlsFlag $clusterFlag $minimalFlag
}

function runRustBenchmark(){
  rustConcurrentTasks=
  for value in $concurrentTasks
  do
    rustConcurrentTasks=$rustConcurrentTasks" --concurrentTasks "$value
  done
  cd ${BENCH_FOLDER}/rust
  cargo run --release -- --resultsFile=../$1 --dataSize $2 $rustConcurrentTasks --host $host --clientCount $clientCount $tlsFlag $clusterFlag $portFlag $minimalFlag
}

function flushDB() {
  cd $utilitiesDir
  npm install
  npx tsc
  npm run flush -- --host $host $tlsFlag $clusterFlag $portFlag
}

function fillDB(){
  flushDB
  npm run fill -- --dataSize $1 --host $host $tlsFlag $clusterFlag $portFlag
}

utilitiesDir=`pwd`/utilities
script=`pwd`/${BASH_SOURCE[0]}
RELATIVE_BENCH_PATH=`dirname ${script}`
export BENCH_FOLDER=`realpath ${RELATIVE_BENCH_PATH}`
export PYTHON_FOLDER="${BENCH_FOLDER}/../python"
export GLIDE_HOME_FOLDER="${BENCH_FOLDER}/.."
export BENCH_RESULTS_FOLDER="${BENCH_FOLDER}/results"
identifier=$(date +"%F")-$(date +"%H")-$(date +"%M")-$(date +"%S")
# Create results folder
mkdir -p $BENCH_RESULTS_FOLDER

function resultFileName() {
    echo results/$namePrefix$1-$2-$identifier.json
}

function Help() {
    echo Running the script without any arguments runs all benchmarks.
    echo Pass -node, -csharp, -python, -java, -go as arguments in order to run the node, csharp, python, java, or go benchmarks accordingly.
    echo Multiple such flags can be passed.
    echo Pass -no-csv to skip analysis of the results.
    echo
    echo Pass -data and then a space-delimited list of sizes for data.
    echo Pass -tasks and then a space-delimited list of number of concurrent operations.
    echo pass -clients and then a space-delimited list of number of the number of clients to be used concurrently.
    echo Pass -prefix with a requested prefix, and the resulting CSV file will have that prefix.
    echo
    echo Example: passing as options \"-node -tasks 10 100 -data 500 20 -clients 1 2 -python -prefix foo \" will cause the node and python benchmarks to run, with the following configurations:
    echo "         1 client, 10 concurrent tasks and 500 bytes of data per value,"
    echo "         1 client, 10 concurrent tasks and 20 bytes of data per value, "
    echo "         1 client, 100 concurrent tasks and 500 bytes of data per value, "
    echo "         1 client, 100 concurrent tasks and 20 bytes of data per value, "
    echo "         2 clients, 10 concurrent tasks and 500 bytes of data per value,"
    echo "         2 clients, 10 concurrent tasks and 20 bytes of data per value, "
    echo "         2 clients, 100 concurrent tasks and 500 bytes of data per value, "
    echo "         2 clients, 100 concurrent tasks and 20 bytes of data per value, "
    echo and the outputs will be saved to a file prefixed with \"foo\".
    echo
    echo Pass -only-glide to only run GLIDE clients.
    echo Pass -is-cluster if the host is a Cluster server. Otherwise the server is assumed to be in standalone mode.
    echo The benchmark will connect to the server using transport level security \(TLS\) by default. Pass -no-tls to connect to server without TLS.
    echo By default, the benchmark runs against localhost. Pass -host and then the address of the requested Redis server in order to connect to a different server.
    echo By default, the benchmark runs against port 6379. Pass -port and then the port number in order to connect to a different port.
    echo By default, the C# benchmark runs with 'net6.0' framework. Pass -dotnet-framework and then the framework version in order to use a different framework.
}

while test $# -gt 0
do
    case "$1" in
        -h) #print help message.
            Help
            exit
            ;;
        -data) # set size of data
            dataSize=$2" "
            shift
            until [[ $2 =~ ^- ]] || [ -z $2 ]; do
                dataSize+=$2"  "
                shift
            done
            ;;
        -prefix) # set size of data
            namePrefix=$2-
            shift
            ;;
        -host)
            host=$2
            shift
            ;;
        -tasks) # set number of concurrent tasks
            concurrentTasks=$2" "
            shift
            until [[ $2 =~ ^- ]] || [ -z $2 ]; do
                concurrentTasks+=$2"  "
                shift
            done
            ;;
        -clients) # set number of clients to use
            clientCount=$2" "
            shift
            until [[ $2 =~ ^- ]] || [ -z $2 ]; do
                clientCount+=$2"  "
                shift
            done
            ;;
        -python)
            runAllBenchmarks=0
            runPython=1
            ;;
        -node)
            runAllBenchmarks=0
            runNode=1
            ;;
        -java)
            runAllBenchmarks=0
            runJava=1
            ;;
        -lettuce)
            runAllBenchmarks=0
            runJava=1
            chosenClients="lettuce"
            ;;
        -jedis)
            runAllBenchmarks=0
            runJava=1
            chosenClients="Jedis"
            ;;
        -go)
            runAllBenchmarks=0
            runGo=1
            ;;
        -go-redis)
            runAllBenchmarks=0
            runGo=1
            chosenClients="go-redis"
            ;;
        -csharp)
            runAllBenchmarks=0
            runCsharp=1
            ;;
        -rust)
            runAllBenchmarks=0
            runRust=1
            ;;
        -only-glide)
            chosenClients="glide"
            ;;
        -no-csv) writeResultsCSV=0 ;;
        -no-tls)
            tlsFlag=
            ;;
        -is-cluster)
            clusterFlag="--clusterModeEnabled"
            ;;
        -port)
            portFlag="--port "$2
            shift
            ;;
        -minimal)
            minimalFlag="--minimal"
            ;;
        -dotnet-framework)
            dotnetFramework=$2
            ;;
    esac
    shift
done

for currentDataSize in $dataSize
do
    if [ -n "$minimalFlag" ];
    then
        echo "Minimal run, not filling database"
        flushDB
    else
        fillDB $currentDataSize
    fi

    if [ $runAllBenchmarks == 1 ] || [ $runPython == 1 ];
    then
        pythonResults=$(resultFileName python $currentDataSize)
        resultFiles+=$pythonResults" "
        runPythonBenchmark $pythonResults $currentDataSize
    fi

    if [ $runAllBenchmarks == 1 ] || [ $runNode == 1 ];
    then
        nodeResults=$(resultFileName node $currentDataSize)
        resultFiles+=$nodeResults" "
        runNodeBenchmark $nodeResults $currentDataSize
    fi

    if [ $runAllBenchmarks == 1 ] || [ $runCsharp == 1 ];
    then
        csharpResults=$(resultFileName csharp $currentDataSize)
        resultFiles+=$csharpResults" "
        runCSharpBenchmark $csharpResults $currentDataSize
    fi

    if [ $runAllBenchmarks == 1 ] || [ $runJava == 1 ];
    then
        javaResults=$(resultFileName java $currentDataSize)
        resultFiles+=$javaResults" "
        runJavaBenchmark $javaResults $currentDataSize
    fi

    if [ $runAllBenchmarks == 1 ] || [ $runGo == 1 ];
    then
        goResults=$(resultFileName go $currentDataSize)
        resultFiles+=$goResults" "
        runGoBenchmark $goResults $currentDataSize
    fi

    if [ $runAllBenchmarks == 1 ] || [ $runRust == 1 ];
    then
        rustResults=$(resultFileName rust $currentDataSize)
        resultFiles+=$rustResults" "
        runRustBenchmark $rustResults $currentDataSize
    fi
done

flushDB

if [ $writeResultsCSV == 1 ];
then
    cd ${BENCH_FOLDER}
    finalCSV=results/$namePrefix""final-$identifier.csv
    $pythonCommand $utilitiesDir/csv_exporter.py $resultFiles$finalCSV
    echo results are in $finalCSV
fi
