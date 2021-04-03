const path = require('path');

module.exports = {
    entry: './src/script.js',
    stats: {
        logging: true,
    },
    output: {
        filename: 'script.js',
        path: path.resolve(__dirname,'..', 'web', 'resources' , 'js'),
    },
    mode: 'development',
    devtool: "source-map",
    module: {
        rules: [
            {
                test: /\.js$/,
                exclude: [
                    /node_modules/
                ],
                use: [
                    { loader: "babel-loader" }
                ]
            }
        ]
    }
};