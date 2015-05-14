(function() {
  'use strict';

  var app = angular.module('myApp');

  // app.factory('Trader', function() {
  // function Trader(id, name) {
  // this.id = id;
  // this.name = name
  // }
  //
  // return Trader;
  // });

  angular.module('myApp').factory('traderList', function() {
    var service = {};
    var traders = {};

    var ws = new WebSocket('ws://localhost:9000/trader/parameters');

    ws.onmessage = function(event) {
      var traderParameters = JSON.parse(event.data);
      console.log(traderParameters);
      traders[traderParameters.id] = traderParameters;
    };

    service.get = function() {
      return traders;
    };

    service.add = function(traderId) {
      if (!traders[traderId]) {
        traders[traderId] = {
          id : traderId
        };
        ws.send('getAllTraderParameters');
      }
      return traders;
    };

    return service;
  });

})();