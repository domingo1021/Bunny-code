while getopts "f:d:c:h:i:" flag
do
        case "${flag}" in
                f) file_name=${OPTARG};;
                d) file_dir=${OPTARG};;
                c) container_name=${OPTARG};;
                h) host_name=${OPTARG};;
                i) identity_file=${OPTARG};;
        esac
done

scp -q -i ${identity_file} ${file_dir}${file_name} ubuntu@${host_name}:~/sandbox_jobs/${file_name}
ssh -i ${identity_file} ubuntu@${host_name} "bash -s ${file_name} ${container_name}" << 'EOF'
#!/bin/bash
file_name=$1
container_name=$3
cd ~/sandbox_jobs
docker run --cpus="0.2" --memory=20m -v $(pwd)/${file_name}:/bunny_code/${file_name} --name ${container_name} node-tool /bunny_code/${file_name}
docker container inspect ${container_name} -f '{"OOM": {{json .State.OOMKilled}}}'
docker rm ${container_name} >/dev/null 2>&1
rm -f ~/sandbox_jobs/${file_name}
EOF