while getopts "f:d:c:h:i:" flag
do
        case "${flag}" in
                f) file_name=${OPTARG};;
                d) target_dir=${OPTARG};;
                c) container_name=${OPTARG};;
                h) host_name=${OPTARG};;
                i) identity_file=${OPTARG};;
        esac
done

scp -q -i ${identity_file} ./${file_name} ubuntu@${host_name}:~/test_slave/${file_name}
ssh -i ${identity_file} ubuntu@${host_name} "bash -s ${file_name} ${target_dir} ${container_name}" << 'EOF'
#!/bin/bash
file_name=$1
target_dir=$2
container_name=$3
cd ~/test_slave
docker run --cpus="0.1" --memory=20m -v $(pwd)/${file_name}:/bunny_code/${target_dir}/${file_name} --name ${container_name} node-tool /bunny_code/${target_dir}/${file_name}
docker container inspect ${container_name} -f '{"OOM": {{json .State.OOMKilled}}}'
docker rm ${container_name} >/dev/null 2>&1
rm -f ~/test_slave/${file_name}
EOF