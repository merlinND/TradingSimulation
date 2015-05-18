(function() {
  'use strict';

  var app = angular.module('myApp');

  /**
   * Service that watches a selected trader and plots a flags series on the the
   * global OHLC graph with transactions corresponding to that trader. The flags
   * series is updated live as new transactions arrive
   */
  angular.module('myApp').factory(
      'ohlcGraphTransactionFlags',
      [
          '$rootScope',
          'ohlcGraph',
          'transactionList',
          function($rootScope, ohlcGraph, transactionList) {
            var service = {};
            var transactions = transactionList.get();
            var chartSeries = ohlcGraph.getChartSeries();
            var chartConfig = ohlcGraph.getChartConfig();
            var flagsSeriesId = "selectedTraderFlags";
            var selectedTrader = {};
            var flagsSeries = {};

            /**
             * Start watching the specified trader Gets all historical
             */
            service.watchTrader = function(trader) {
              selectedTrader = trader;
              initFlagsSeries(trader);
              flagsSeries.data = getFlagsData();
            };

            /**
             * Gets all transaction data for the specified trader and formats it
             * as an array highcharts flags series data
             */
            function getFlagsData() {
              var filtered = transactions.filter(function(t) {
                return isTransactionForSelectedTrader(t);
              });
              return filtered.map(function(t) {
                return formatFlagsData(t);
              });
            }

            /**
             * Creates the transaction flags series with empty data on the graph
             * or overrides the existing transaction flags series
             */
            function initFlagsSeries(trader) {
              flagsSeries = chartSeries.filter(function(series) {
                return series.id == flagsSeriesId;
              })[0];

              if (!flagsSeries) {
                flagsSeries = {
                  type : 'flags',
                  id : flagsSeriesId,
                  name : "Transactions of " + trader.name,
                  shape : 'circlepin',
                  color : '#df691a',
                  fillColor : '#df691a',
                  onSeries : chartSeries[0].id, // TODO: handle multiple series
                  style : {
                    color : 'white'
                  },
                  width : 16,
                  data : []
                };
                chartSeries.push(flagsSeries);
              }

              flagsSeries.name = "Transactions of " + trader.name;
              flagsSeries.data = [];
            }

            /**
             * Format a transaction as a buy (B) or sell (S) flag
             */
            function formatFlagsData(transaction) {
              var title = (transaction.buyerId == selectedTrader.id) ? 'B'
                  : 'S';
              return {
                x : transaction.timestamp,
                title : title
              };
            }

            /**
             * Returns true if either the buyerId or sellerId of the transaction
             * corresponds to the selected trader id
             */
            function isTransactionForSelectedTrader(transaction) {
              return transaction.buyerId == selectedTrader.id || transaction.sellerId == selectedTrader.id;
            }

            /**
             * Listens to new transactions and updates the transaction flags
             * series if the transaction corresponds to the selected trader
             */
            $rootScope.$on('transactions:updated',
                function(event, transaction) {
                  if (isTransactionForSelectedTrader(transaction)) {
                    flagsSeries.data.push(formatFlagsData(transaction));
                  }
                });

            return service;
          } ]);

})();