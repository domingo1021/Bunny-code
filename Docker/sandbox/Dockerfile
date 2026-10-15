# The image sandbox hosts run user code in (tag it `node-tool`, see bunny.execution.image).
# The API starts it with `--entrypoint node … -` and streams the program over stdin, with
# --network none, --read-only, --cap-drop ALL, --pids-limit and the 0.2 CPU / 20 MB limits.
FROM node:24-slim
USER node
WORKDIR /home/node
ENTRYPOINT ["node"]
