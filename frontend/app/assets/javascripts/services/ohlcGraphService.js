(function() {
  'use strict';

  var app = angular.module('myApp');

  /**
   * Service that listens to OHLC data and prepares a highcharts-ng candlestick
   * graph
   */
  angular.module('myApp').factory(
      'ohlcGraph',
      [
          '$rootScope',
          function($rootScope) {
            var service = {};
            var chartSeries = [];

            /**
             * Chart configuration
             */
            var groupingUnits = [ [ 'week', [ 1 ] ], [ 'day', [ 1 ] ],
                [ 'month', [ 1, 2, 3, 6 ] ] ];
            var chartConfig = {
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
                } ],
                // navigator
                navigator : {
                  enabled : true,
                  series : {
                    data : chartSeries[0]
                  }
                },
                // plot options
                plotOptions : {
                  candlestick : {
                    color : '#5cb85c',
                    upColor : '#d9534f'
                  }
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
                    count : 777,
                    text : '1 week'
                  }, {
                    type : 'month',
                    count : 1,
                    text : '1 month'
                  }, {
                    type : 'month',
                    count : 3,
                    text : '2 months'
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
              series : chartSeries,
              title : {
                text : 'Market price'
              },
              credits : {
                enabled : false
              },
              loading : true,
              size : {}
            };

            var ws = new WebSocket('ws://localhost:9000/market/ohlc');

            /**
             * Handles OHLC data from the websocket Creates a separate
             * candlestick series for every symbol and appends new datapoints to
             * existing series
             */
            ws.onmessage = function(event) {
              var symbolOhlc = JSON.parse(event.data);
              var name = symbolOhlc.whatC.s + " to " + symbolOhlc.withC.s;
              var id = symbolOhlc.whatC.s + "-" + symbolOhlc.withC.s;
              var ohlc = symbolOhlc.ohlc;
              var ohlcSeries = getChartSeriesById(id);

              if (!ohlcSeries) {
                initOhlcSeries(id, name)
              }

              $rootScope.$apply(function() {
                ohlcSeries.data.push([ ohlc.timestamp, ohlc.open, ohlc.high,
                    ohlc.low, ohlc.close ]);

                if (chartConfig.loading) {
                  chartConfig.loading = false;
                }
              });

            };

            /**
             * Finds a chart series by its id
             */
            function getChartSeriesById(id) {
              return chartSeries.filter(function(series) {
                return series.id == id;
              })[0];
            }

            /**
             * Initializes a new OHLC candlestick series By default, only one
             * series is set to visible
             */
            function initOhlcSeries(id, name) {
              ohlcSeries = {
                type : 'candlestick',
                name : name,
                id : id,
                visible : false,
                data : []
              };

              if (chartSeries.length === 0) {
                ohlcSeries.visible = true;
              }

              chartSeries.push(ohlcSeries);
            }

            /**
             * Returns the chart configuration needed for the directive in the
             * controller
             */
            service.getChartConfig = function() {
              return chartConfig;
            };

            /**
             * Returns the chart series
             */
            service.getChartSeries = function() {
              return chartSeries;
            };

            return service;
          } ]);

})();