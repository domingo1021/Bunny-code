while getopts "h:" flag
do
        case "${flag}" in
                h) host_name=${OPTARG};;
        esac
done

curl --max-time 3 http://${host_name}:9100/metrics | grep -E "^node_cpu_seconds"