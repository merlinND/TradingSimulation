(function() {
  'use strict';

  angular.module('myApp').controller(
      'OhlcGraphController',
      [
          '$scope',
          'alertService',
          function($scope, alertService) {
            $scope.alerts = alertService.get();
            $scope.ohlcData = [];
            $scope.volumeData = [];

            var ws = new WebSocket('ws://localhost:9000/market/ohlc');

            ws.onmessage = function(event) {
              var ohlc = JSON.parse(event.data);
              $scope.$apply(function() {

                $scope.ohlcData.push([ ohlc.timestamp, ohlc.open, ohlc.high, ohlc.low,
                    ohlc.close ]);
                $scope.volumeData.push([ ohlc.timestamp, ohlc.volume ]);

                if ($scope.chartConfig.loading) {
                  $scope.chartConfig.loading = false;
                }

              });
            };

            ws.onclose = function(event) {
              $scope.$apply(function() {
                alertService.add('info', 'Closed connection to the backend');
              });
            };

            ws.onerror = function(event) {
              $scope.$apply(function() {
                alertService.add('danger', 'Lost connection to the backend');
              });
            };

            $scope.chartSeries = [];

            $scope.chartConfig = {
              options : {
                // axis
                xAxis : [ {
                  type : 'datetime'
                } ],
                yAxis : [ {
                  labels : {
                    align : 'right',
                    x : -3
                  },
                  title : {
                    text : 'OHLC'
                  },
                  height : '60%',
                  lineWidth : 2
                }, {
                  labels : {
                    align : 'right',
                    x : -3
                  },
                  title : {
                    text : 'Volume'
                  },
                  top : '65%',
                  height : '35%',
                  offset : 0,
                  lineWidth : 2
                } ],
                // navigator
                navigator : {
                  enabled : true,
                  series : {
                    data : $scope.chartSeries[0]
                  }
                },
                // plot options
                plotOptions : {
                  candlestick : {
                    color : 'green',
                    upColor : 'red'
                  }
                },
                legend: {
                  enabled: false
                },
                // range selector and buttons
                rangeSelector : {
                  enabled : true,
                  buttonTheme : {
                    width : null,
                    padding : 2
                  },
                  buttons : [ {
                    type : 'day',
                    count : 1,
                    text : '1 day'
                  }, {
                    type : 'month',
                    count : 7,
                    text : '1 week'
                  }, {
                    type : 'month',
                    count : 1,
                    text : '1 month'
                  }, {
                    type : 'month',
                    count : 6,
                    text : '6 months'
                  }, {
                    type : 'year',
                    count : 1,
                    text : '1 year'
                  }, {
                    type : 'all',
                    text : 'All'
                  } ]
                },
              },
              // data series
              series : [ {
                type : 'candlestick',
                name : 'OHLC',
                data : $scope.ohlcData,
              }, {
                type : 'column',
                name : 'Volume',
                data : $scope.volumeData,
                yAxis : 1,
              } ],
              title : {
                text : 'Market price'
              },
              credits : {
                enabled : false
              },
              loading : true,
              size : {}
            };

          } ]);

})();