(function() {
  'use strict';

  var app = angular.module('myApp');

  /**
   * Service to build a collection of known traders and their parameters
   */
  angular.module('myApp').factory('traderList',
      [ '$rootScope', function($rootScope) {
        var service = {};
        var traders = {};

        /**
         * Listens to traderParameter messages and updates the trader in the
         * collection of known traders with the parameter
         */
        var ws = new WebSocket('ws://localhost:9000/trader/parameters');
        ws.onmessage = function(message) {
          var traderParameters = JSON.parse(message.data);
          traders[traderParameters.id] = traderParameters;
          $rootScope.$broadcast('traders:updated', traderParameters);
        };

        /**
         * Returns the collection of known traders
         */
        service.get = function() {
          return traders;
        };

        /**
         * Adds a new trader to the collection of known traders If the trader is
         * not already known, it asks for its parameters
         */
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