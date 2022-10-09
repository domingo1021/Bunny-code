FROM node:18-slim

COPY . .

RUN npm install

EXPOSE 3000

ENTRYPOINT ["node"]

CMD ["socket.js"]
