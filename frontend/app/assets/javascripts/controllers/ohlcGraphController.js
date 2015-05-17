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
            $scope.chartSeries = ohlcGraph.getChartSeries();
            $scope.chartConfig = ohlcGraph.getChartConfig();
            
            var ws = new WebSocket('ws://localhost:9000/market/ohlc');

            ws.onmessage = function(event) {
              var symbolOhlc = JSON.parse(event.data);
              $scope.$apply(function() {
                var name = symbolOhlc.whatC.s + " to " + symbolOhlc.withC.s;
                var id = symbolOhlc.whatC.s + "-" + symbolOhlc.withC.s;
                var ohlc = symbolOhlc.ohlc;
                var ohlcSeries = $scope.chartSeries.filter(function(series) {
                  return series.id == id;
                })[0];

                if (!ohlcSeries) {
                  ohlcSeries = {
                    type : 'candlestick',
                    name : name,
                    id: id,
                    visible : false,
                    data : []
                  };

                  if ($scope.chartSeries.length === 0) {
                    ohlcSeries.visible = true;
                  }

                  $scope.chartSeries.push(ohlcSeries);
                }

                ohlcSeries.data.push([ ohlc.timestamp, ohlc.open, ohlc.high,
                    ohlc.low, ohlc.close ]);

                if ($scope.chartConfig.loading) {
                  $scope.chartConfig.loading = false;
                }
              });
            };




          } ]);

})();