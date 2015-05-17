(function() {
  'use strict';

  var app = angular.module('myApp');

  angular.module('myApp').factory('traderList', function() {
    var service = {};
    var traders = {};

    var ws = new WebSocket('ws://localhost:9000/trader/parameters');

    ws.onmessage = function(event) {
      var traderParameters = JSON.parse(event.data);
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