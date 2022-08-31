FROM node:slim

WORKDIR /app

COPY . .

RUN npm install

ENTRYPOINT ["node"]

CMD ["/app/tool.js"]
