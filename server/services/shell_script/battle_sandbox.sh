while getopts "f:d:c:h:i:e:s:" flag
do
        case "${flag}" in
                f) file_name=${OPTARG};;
                d) target_dir=${OPTARG};;
                e) test_case_env=${OPTARG};;
                s) test_case_script=${OPTARG};;
                c) container_name=${OPTARG};;
                h) host_name=${OPTARG};;
                i) identity_file=${OPTARG};;
        esac
done

scp -q -i ${identity_file} ${file_dir}${file_name} ubuntu@${host_name}:~/sandbox_jobs/${file_name}
ssh -i ${identity_file} ubuntu@${host_name} "bash -s ${file_name} ${test_case_env} ${test_case_script} ${container_name}" << 'EOF'
#!/bin/bash
file_name=$1
test_case_env=$2
test_case_script=$3
container_name=$4
cd ~/sandbox_jobs
docker run --cpus="0.2" --memory=20m -e ${test_case_env}=${file_name} -v $(pwd)/${file_name}:/bunny_code/${file_name} --name ${container_name} sandbox /bunny_code/${test_case_script}
docker container inspect ${container_name} -f '{"OOM": {{json .State.OOMKilled}}}'
docker rm ${container_name} >/dev/null 2>&1
rm -f ~/sandbox_jobs/${file_name}
EOF