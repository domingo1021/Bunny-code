while getopts "h:" flag
do
        case "${flag}" in
                h) host_name=${OPTARG};;
        esac
done

curl http://${host_name}:9100/metrics | grep -E "^node_memory_MemAvailable_bytes|^node_memory_MemTotal_bytes"