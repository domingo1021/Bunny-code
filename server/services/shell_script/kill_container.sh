while getopts "c:h:i:" flag
do
        case "${flag}" in
                c) container_name=${OPTARG};;
                h) host_name=${OPTARG};;
                i) identity_file=${OPTARG};;
        esac
done

ssh -i ${identity_file} ubuntu@${host_name} "bash -s ${container_name}" << 'EOF'
#!/bin/bash
container_name=$1
docker rm -f ${container_name}
EOF