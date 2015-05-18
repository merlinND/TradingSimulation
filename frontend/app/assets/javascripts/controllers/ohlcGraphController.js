(function() {
  'use strict';

  angular.module('myApp').controller(
      'OhlcGraphController',
      [
          '$scope',
          'alertService',
          'ohlcGraph',
          function($scope, alertService, ohlcGraph) {
            $scope.alerts = alertService.get();
            $scope.chartConfig = ohlcGraph.getChartConfig();

          } ]);

})();