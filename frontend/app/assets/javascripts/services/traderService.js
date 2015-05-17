(function() {
  'use strict';

  var app = angular.module('myApp');

  angular.module('myApp').factory('traderList',
      [ '$rootScope', function($rootScope) {
        var service = {};
        var traders = {};

        var ws = new WebSocket('ws://localhost:9000/trader/parameters');

        ws.onmessage = function(message) {
          var traderParameters = JSON.parse(message.data);
          traders[traderParameters.id] = traderParameters;
          $rootScope.$broadcast('traders:updated', traderParameters);
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
      } ]);

})();