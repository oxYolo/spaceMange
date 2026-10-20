/* 运行时环境配置（部署时可直接改此文件，无需重新构建） */
(function () {
  // 天地图 token 池（取自参考工程 spatiotemporal-intelligence-front/public/config/urlConfig.js）
  var tdtTokenList = [
    "618023f041116049b146747c1e243e52",
    "c29c983763d7f0d32bc1299494c4b5d9",
    "11950a6cce639481b6f43baf4194725a",
    "f58dc51e294d79802fb88e6f8d08b8f1",
    "fc06adc7d918613862943f2f742e4a9d",
    "0e11045c92deb48f3e705a0b9073dd12",
    "aad3efefa49c4be7f6e7b4330f174673",
    "aeda1ddab3132aeb40a8643a8031a986",
    "b82999470400ed4c8866340057dd8129",
    "069d823c5e6ffbff0b5be6330d7f08be",
    "a2e97143d0f49c5dc3d76bdd7cdcb5b3",
    "c4540c0bb001f750104db13eefc01e31",
    "22424d4074c68ed54a98a09ba314d1b7",
    "1ebaa19a3ddf46d067cdecde3b5144c9",
    "cf9118cb438b74a33d6741a3915b8b1c",
    "15e206962a15e824011a038a3b4f7036",
    "871b22c783b40aa7ec7462a02b9f1028",
    "e3c050960c097ddb2b0e911ed1b26036",
    "04801d3ffbfe3ed0423d519d09f6c5d7"
  ];

  // 开发环境：走 Vite 代理 /api → http://localhost:8982
  // imUrl：.NET IM 服务（Lri.IMCore，SignalR /MessageHub，HTTP 端口 8082）；
  // 浏览器直连需将该来源列入服务端 CorsDomain（仓库已加入 9200 开发来源）
  var development = {
    systemName: "空间数据管理控制台",
    envName: "开发",
    baseUrl: "/api",
    baseApiUrl: "/base-api",
    uploadChunkSize: 5 * 1024 * 1024,
    imUrl: "http://127.0.0.1:8082"
  };

  // 测试环境（可按需修改）
  var test = {
    systemName: "空间数据管理控制台",
    envName: "测试",
    baseUrl: "/api",
    baseApiUrl: "/base-api",
    uploadChunkSize: 5 * 1024 * 1024,
    imUrl: ""
  };

  // 生产环境：与后端同域，由 nginx 反代到 geodata-service:8982
  // imUrl：.NET IM 服务（SignalR /MessageHub）地址；留空则禁用实时进度推送（走 5s 轮询兜底）
  var production = {
    systemName: "空间数据管理控制台",
    envName: "生产",
    baseUrl: "",
    baseApiUrl: "/base-api",
    uploadChunkSize: 5 * 1024 * 1024,
    imUrl: ""
  };

  // 通用配置（地图底图 / 默认视图中心：赤壁市）
  var common = {
    tdtUrl: "http://t0.tianditu.com",
    tdtUrlHttps: "https://t0.tianditu.com",
    mapCenter: [113.9042, 29.7234],
    mapZoom: 11,
    cesiumCenter: [113.9042, 29.7234, 40000],
    getTdtToken: function () {
      return tdtTokenList[Math.floor(Math.random() * tdtTokenList.length)];
    }
  };

  var services = {
    development: development,
    test: test,
    production: production
  };

  window.getUrlConfig = function (env) {
    var base = services[env] || services.production;
    return Object.assign({}, base, common);
  };
})();
